package example.streaming.config.mvc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.MethodParameter;
import org.springframework.ui.Model;
import org.springframework.util.Assert;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.annotation.ModelMethodProcessor;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

import example.streaming.AsyncModel;

public class AsyncModelConfig {

    // Prevents waiting forever and should be longer than any actual request.
    private static final int DEFAULT_TIMEOUT_SECONDS = 10 * 60;

    @Configuration
    public static class WebConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
            argumentResolvers.add(new AsyncModelArgumentResolver());
        }

        @Bean @ConditionalOnBean(ExecutorService.class)
        public FutureUpgrader getFutureUpgrader(ExecutorService executorService) {
            return new FutureUpgrader(executorService, DEFAULT_TIMEOUT_SECONDS); // For AsyncModel
        }
    }


    private static class AsyncModelArgumentResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return AsyncModel.class == parameter.getParameterType();
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
            Assert.notNull(mavContainer, "ModelAndViewContainer is required for model exposure");
            Object model = mavContainer.getModel();
            if (model == mavContainer.getDefaultModel()) { // Not redirect
                Assert.isInstanceOf(Model.class, model);
                model = new UpgradeableFutureAsyncModel((Model) model);
            }
            return model;
        }

    }


    @Configuration @Lazy(false)
    public static class FixResolverOrderConfig implements InitializingBean {
        @Autowired
        RequestMappingHandlerAdapter handlerAdapter;

        @Override
        public void afterPropertiesSet() {
            List<HandlerMethodArgumentResolver> resolvers = handlerAdapter.getArgumentResolvers();
            Objects.requireNonNull(resolvers);

            OptionalInt syncIdx = IntStream.range(0, resolvers.size())
                    .filter(i-> resolvers.get(i) instanceof ModelMethodProcessor)
                    .findFirst();
            if (!syncIdx.isPresent()) {
                return;
            }

            OptionalInt asyncIdx = IntStream.range(syncIdx.getAsInt() + 1, resolvers.size())
                    .filter(i-> resolvers.get(i) instanceof AsyncModelArgumentResolver)
                    .findFirst();
            if (!asyncIdx.isPresent()) {
                return;
            }

            // If AsyncModel resolver is not before the Model one,
            // then it won't work, since the former interface extends the latter.
            List<HandlerMethodArgumentResolver> orderedResolvers = new ArrayList<>(resolvers);
            HandlerMethodArgumentResolver async = orderedResolvers.remove(asyncIdx.getAsInt());
            orderedResolvers.add(syncIdx.getAsInt(), async);
            handlerAdapter.setArgumentResolvers(orderedResolvers);
        }
    }


    private static class UpgradeableFutureAsyncModel extends WrappingModel implements AsyncModel {
        private UpgradeableFutureAsyncModel(Model model) {
            super(model);
        }

        @Override
        public <T> AsyncValue<T> addAttribute(String attributeName, Callable<T> callable) {
            AsyncValue<T> future = new AsyncValueUpgradeableFuture<>(callable);
            super.addAttribute(attributeName, future);
            return future;
        }

        @Override
        public <T> void addUnordered(String attributeName, Callable<T>[] callables) {
            Collection<Future<T>> futures = new UpgradeableFutureCollection<>(callables, true);
            super.addAttribute(attributeName, futures);
        }

        private static class AsyncValueUpgradeableFuture<T> extends UpgradeableFuture<T> implements AsyncValue<T> {
            AsyncValueUpgradeableFuture(Callable<T> callable) {
                super(callable);
            }

            // Allow other passed in callables calling this to catch their
            // business logic exceptions as if there wasn't a future involved.
            // For things like InterruptedExceptions and TimeoutException
            // it won't be obvious they're there, and they can just propagate.
            // Also ensure that there's always a timeout involved.
            @Override
            public T await() throws Exception {
                try {
                    return get(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof Exception
                            && !(cause instanceof InterruptedException)
                            && !(cause instanceof CancellationException)) {
                        throw (Exception) cause;
                    }
                    throw e;
                }
            }
        }
    }

    private static class WrappingModel implements Model {
        private final Model source;
        WrappingModel(Model model) {
            this.source = model;
        }

        @Override
        public Model addAttribute(String name, Object value) {
            source.addAttribute(name, value);
            return this;
        }
        @Override
        public Model addAttribute(Object value) {
            source.addAttribute(value);
            return this;
        }
        @Override
        public Model addAllAttributes(Collection<?> values) {
            source.addAllAttributes(values);
            return this;
        }
        @Override
        public Model addAllAttributes(Map<String, ?> attributes) {
            source.addAllAttributes(attributes);
            return this;
        }
        @Override
        public Model mergeAttributes(Map<String, ?> attributes) {
            source.mergeAttributes(attributes);
            return this;
        }
        @Override
        public boolean containsAttribute(String name) {
            return source.containsAttribute(name);
        }
        @Override
        public Object getAttribute(String name) {
            return source.getAttribute(name);
        }
        @Override
        public Map<String, Object> asMap() {
            return source.asMap();
        }
    }

}

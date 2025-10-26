package example.streaming.config.mvc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Configuration
    public static class WebConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
            argumentResolvers.add(new AsyncModelArgumentResolver());
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
        public <T> Future<T> addAttribute(String attributeName, Callable<T> callable) {
            Future<T> future = new UpgradeableFuture<>(callable);
            super.addAttribute(attributeName, future);
            return future;
        }

        @Override
        public <T> void addUnordered(String attributeName, Callable<T>[] callables) {
            Collection<Future<T>> futures = new UpgradableFutureCollection<>(callables);
            super.addAttribute(attributeName, futures);
        }
    }

    private static class WrappingModel implements Model {
        private final Model source;
        WrappingModel(Model model) {
            this.source = model;
        }

        @Override
        public Model addAttribute(String name, Object value) {
            return source.addAttribute(name, value);
        }
        @Override
        public Model addAttribute(Object value) {
            return source.addAttribute(value);
        }
        @Override
        public Model addAllAttributes(Collection<?> values) {
            return source.addAllAttributes(values);
        }
        @Override
        public Model addAllAttributes(Map<String, ?> attributes) {
            return source.addAllAttributes(attributes);
        }
        @Override
        public Model mergeAttributes(Map<String, ?> attributes) {
            return source.mergeAttributes(attributes);
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

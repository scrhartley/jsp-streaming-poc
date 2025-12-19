package example.streaming.config.mvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.ExecutorService;
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
                model = new UpgradeableFutureAsyncModel((Model) model, DEFAULT_TIMEOUT_SECONDS);
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

}

package example.streaming.config.jsp;

import static example.streaming.config.jsp.StreamingJspExceptionHandler.*;
import static java.util.Collections.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.view.AbstractUrlBasedView;
import org.springframework.web.servlet.view.InternalResourceView;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.springframework.web.servlet.view.JstlView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import example.streaming.config.mvc.FutureUpgrader;
import example.streaming.config.mvc.UpgradeableFutureCollection;

// Originally added to handle LazyInvocableJspValueException.
@Configuration
public class JspConfig {

    // Note: Error path should prevent caching to avoid being cached as the content of the requested page.
    private static final String ERROR_PATH = "/error/500.html";
    private static final StreamingJspExceptionHandler STREAMING_EXCEPTION_HANDLER = ExceptionHandlers.HTML_DEBUG;
    private static boolean CANCEL_UNCOMPLETED_FUTURES = true; // Intended to avoid leaving stuck threads.
    // Prevents waiting forever and should be longer than any actual request.
    private static final int DEFAULT_TIMEOUT_SECONDS = 60 * 10;

    @Bean
    public InternalResourceViewResolver defaultViewResolver(
            WebMvcProperties mvcProperties, @Autowired(required = false) ExecutorService executorService) {
        FutureUpgrader futureUpgrader = executorService == null ? null
                : new FutureUpgrader(executorService, DEFAULT_TIMEOUT_SECONDS); // For AsyncModel
        InternalResourceViewResolver resolver = new InternalResourceViewResolver() {
            @Override
            protected AbstractUrlBasedView instantiateView() {
                return getViewClass() == InternalResourceView.class ? new EnhancedInternalResourceView(futureUpgrader) :
                        (getViewClass() == JstlView.class ? new EnhancedJstlView(futureUpgrader) : super.instantiateView());
            }
        };
        resolver.setPrefix(mvcProperties.getView().getPrefix());
        resolver.setSuffix(mvcProperties.getView().getSuffix());
        return resolver;
    }


    private static void handleException(Exception e, HttpServletResponse response) throws Exception {
        e = tryUnwrapLazyValueException(e);

        // If we're allowing flushes in order to support streaming, we
        // have to assume that a flush may already have happened even if
        // this particular exception is not directly related to that
        // (so always use our stream exception handling mechanism).
        try {
            PrintWriter out = response.getWriter();
            STREAMING_EXCEPTION_HANDLER.writeErrorHtml(out, e);
            // Close the stream so the browser will act upon a meta refresh
            // redirect and not log an incomplete stream error in the console.
            out.close();
        }
        catch (Exception ex) {
            e.addSuppressed(ex);
        }

        throw e;
    }

    private static Exception tryUnwrapLazyValueException(Exception e) {
        if (e.getCause() instanceof LazyInvocableJspValueException) {
            e = (Exception) e.getCause();
        }
        else if (e instanceof ServletException && e.getCause() != null
                && e.getCause().getCause() instanceof LazyInvocableJspValueException) {
            e = (Exception) e.getCause().getCause();
        }
        if (e instanceof LazyInvocableJspValueException) {
            if (e.getCause() instanceof Exception) {
                e = (Exception) e.getCause();
            }
        }
        return e;
    }


    private static class EnhancedJstlView extends JstlView {
        private final FutureUpgrader futureUpgrader;
        EnhancedJstlView(FutureUpgrader futureUpgrader) {
            this.futureUpgrader = futureUpgrader;
        }

        @Override
        public void render(@Nullable Map<String, ?> model, HttpServletRequest request,
                           HttpServletResponse response) throws Exception {
            List<Future<?>> futures = CANCEL_UNCOMPLETED_FUTURES ? getFutures(model) : emptyList();
            try {
                if (futureUpgrader != null) futureUpgrader.upgradeFutures(model);
                super.render(model, request, response);
            } catch (Exception e) {
                handleException(e, response);
            } finally {
                for (Future<?> future : futures) {
                    future.cancel(true);
                }
            }
        }
    }
    private static class EnhancedInternalResourceView extends InternalResourceView {
        private final FutureUpgrader futureUpgrader;
        EnhancedInternalResourceView(FutureUpgrader futureUpgrader) {
            this.futureUpgrader = futureUpgrader;
        }

        @Override
        public void render(@Nullable Map<String, ?> model, HttpServletRequest request,
                           HttpServletResponse response) throws Exception {
            List<Future<?>> futures = CANCEL_UNCOMPLETED_FUTURES ? getFutures(model) : emptyList();
            try {
                if (futureUpgrader != null) futureUpgrader.upgradeFutures(model);
                super.render(model, request, response);
            } catch (Exception e) {
                handleException(e, response);
            } finally {
                for (Future<?> future : futures) {
                    future.cancel(true);
                }
            }
        }
    }

    private static List<Future<?>> getFutures(@Nullable Map<String, ?> model) {
        if (model == null || model.isEmpty()) {
            return emptyList();
        }

        List<Future<?>> futures = new ArrayList<>();
        for (Object value : model.values()) {
            if (value instanceof Future) {
                futures.add((Future<?>) value);
            } else if (value instanceof UpgradeableFutureCollection) {
                futures.addAll(((UpgradeableFutureCollection<?>) value).getFuturesPreUpgrade());
            }
        }
        return futures;
    }


    private interface ExceptionHandlers {
        StreamingJspExceptionHandler META_REDIRECT = new MetaRedirectHandler(ERROR_PATH);
        StreamingJspExceptionHandler HTML_DEBUG    = new HtmlDebugHandler(); // For dev
    }

}

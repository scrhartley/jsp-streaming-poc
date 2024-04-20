package example.streaming.jsp;

import static example.streaming.jsp.StreamingJspExceptionHandler.*;

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
import javax.servlet.jsp.JspException;
import java.io.PrintWriter;
import java.util.Map;

// Originally added to handle LazyInvocableJspValueException.
@Configuration
public class JspConfig {

    // Note: Error path should prevent caching to avoid being cached as the content of the requested page.
    private static final String ERROR_PATH = "/error/500.html";
    private static final StreamingJspExceptionHandler STREAMING_EXCEPTION_HANDLER = ExceptionHandlers.META_REDIRECT;

    @Bean
    public InternalResourceViewResolver defaultViewResolver(WebMvcProperties mvcProperties) {
        InternalResourceViewResolver resolver = new InternalResourceViewResolver() {
            @Override
            protected AbstractUrlBasedView instantiateView() {
                return (getViewClass() == InternalResourceView.class ? new EnhancedInternalResourceView() :
                        (getViewClass() == JstlView.class ? new EnhancedJstlView() : super.instantiateView()));
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
        @Override
        public void render(@Nullable Map<String, ?> model, HttpServletRequest request,
                           HttpServletResponse response) throws Exception {
            try {
                super.render(model, request, response);
            } catch (Exception e) {
                handleException(e, response);
            }
        }
    }
    private static class EnhancedInternalResourceView extends InternalResourceView {
        @Override
        public void render(@Nullable Map<String, ?> model, HttpServletRequest request,
                           HttpServletResponse response) throws Exception {
            try {
                super.render(model, request, response);
            } catch (Exception e) {
                handleException(e, response);
            }
        }
    }


    private interface ExceptionHandlers {
        StreamingJspExceptionHandler META_REDIRECT = new MetaRedirectHandler(ERROR_PATH);
        StreamingJspExceptionHandler HTML_DEBUG    = new HtmlDebugHandler(); // For dev
    }

}

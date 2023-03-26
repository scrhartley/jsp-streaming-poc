package simon.example.streaming.jsp;

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

// Exists to handle LazyInvocableJspValueException.
@Configuration
public class JspConfig {

    // Note: Error path should prevent caching to avoid being cached as the content of the requested page.
    private static String ERROR_PATH = "/error/500.html";

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
        if (e.getCause() instanceof LazyInvocableJspValueException) {
            e = (Exception) e.getCause();
        }
        else if (e instanceof ServletException && e.getCause() instanceof JspException
                && e.getCause().getCause() instanceof LazyInvocableJspValueException) {
            e = (Exception) e.getCause().getCause();
        }
        if (e instanceof LazyInvocableJspValueException) {
            if (e.getCause() instanceof Exception) {
                e = (Exception) e.getCause();
            }
            handleLazyPageError(response);
        }
        throw e;
    }
    private static void handleLazyPageError(HttpServletResponse response) {
        try {
            PrintWriter out = response.getWriter();
            out.print(String.format("<meta http-equiv=\"refresh\" content=\"0; url=%s\">", ERROR_PATH));
            // Close the stream so that the browser thinks it should act upon the redirect,
            // rather than just log the incomplete stream error in the console.
            out.close();
        }
        catch (Exception e) { } // Throw original exception, not this.
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

}

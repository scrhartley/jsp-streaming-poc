package example.streaming.jsp;

import javax.el.ELContext;
import javax.el.ELException;
import javax.el.TypeConverter;
import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.BodyContent;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

class FutureELResolver extends TypeConverter {

    @Override
    public Object convertToType(ELContext elContext, Object obj, Class<?> type) {
        if (obj instanceof Future<?>) {
//            elContext.setPropertyResolved(obj, type);
            elContext.setPropertyResolved(true);

            try {
                // Send the already finished content to the browser (streaming or chunked transfer-encoding).
                JspContext jspContext = (JspContext) elContext.getContext(JspContext.class);
                JspWriter writer = jspContext.getOut();
                if (shouldAutoFlush(jspContext) && !(writer instanceof BodyContent)) {
                    writer.flush();
                }
            } catch (Exception e) {
                throw new ELException(e);
            }

            try {
                return ((Future<?>) obj).get();
            } catch (ExecutionException e) {
                handleException(e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                handleException(e);
            } catch (RuntimeException e) {
                handleException(e);
            }
        }
        return null;
    }

    private void handleException(Throwable e) {
        // We usually want to do things such as write to the real JspWriter here and then close it,
        // but the JSP machinery doesn't log the correct exception if we do this,
        // and we might not be writing to the main writer when we want to.
        // Push the problem up the stack by using a special exception which is handled elsewhere.
        throw new LazyInvocableJspValueException(e);
    }

    private static boolean shouldAutoFlush(JspContext jspContext) {
        // This is a hack for demo purposes to easily disable auto-flushing from the template.
        Boolean override = (Boolean) jspContext.getAttribute("AUTO_FLUSH");
        return (override != null) ? override : true;
    }

}

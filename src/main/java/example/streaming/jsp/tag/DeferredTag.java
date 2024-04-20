package example.streaming.jsp.tag;

import java.io.IOException;
import java.io.StringWriter;
import java.util.LinkedHashMap;

import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.JspFragment;
import javax.servlet.jsp.tagext.SimpleTagSupport;

public class DeferredTag extends SimpleTagSupport {

    private static final String SHARED_FALLBACK_NAME = "sharedDeferredFallback";
    private static final int SHARED_FALLBACK_SCOPE = PageContext.REQUEST_SCOPE;
    private static final String ID_COUNTER_ATTRIBUTE_KEY = "_}\n$%___DEFERRED_ID___%$\n{_";
    private static final int ID_COUNTER_ATTRIBUTE_SCOPE = PageContext.PAGE_SCOPE;
    private static final String PENDING_ATTRIBUTE_KEY = "_}\n$%___DEFERRED_PENDING___%$\n{_";
    private static final int PENDING_ATTRIBUTE_SCOPE = PageContext.PAGE_SCOPE;
    private static final String FALLBACK_ID_PREFIX = "JD_fb:";
    static final String START_DATA = "JD$";
    static final String END_DATA = "/JD$";

    private String fallback;
    private JspFragment fallbackFragment;

    public void setFallback(String fallback) {
        this.fallback = fallback;
    }
    public void setFallbackFragment(JspFragment fallback) {
        this.fallbackFragment = fallback;
    }

    @Override
    public void doTag() throws JspException, IOException {
        if (fallback != null && fallbackFragment != null) {
            throw new JspTagException("Only use one of fallback and fallbackFragment");
        }

        JspContext jspContext = getJspContext();
        JspWriter out = jspContext.getOut();
        String fallbackId = getNextFallbackId(jspContext);

        if (fallback != null) {
            out.write(buildFallbackContent(fallback, fallbackId));
            addBodyToPending(fallbackId, jspContext);
        } else if (fallbackFragment != null) {
            StringWriter temp = new StringWriter();
            fallbackFragment.invoke(temp);
            out.write(buildFallbackContent(temp, fallbackId));
            addBodyToPending(fallbackId, jspContext);
        } else {
            Object defaultFallback = jspContext.getAttribute(SHARED_FALLBACK_NAME, SHARED_FALLBACK_SCOPE);
            out.write(buildFallbackContent(defaultFallback, fallbackId));
            addBodyToPending(fallbackId, jspContext);
        }
    }


    private static String buildFallbackContent(Object fallback, String id) {
        return "<!--" + START_DATA + "-->" +
                "<template id=\"" + id + "\"></template>" + fallback +
                "<!--" + END_DATA + "-->";
    }

    private static String getNextFallbackId(JspContext jspContext) {
        Integer counter = (Integer) jspContext
                .getAttribute(ID_COUNTER_ATTRIBUTE_KEY, ID_COUNTER_ATTRIBUTE_SCOPE);
        counter = counter==null ? 1 : counter + 1;
        jspContext.setAttribute(ID_COUNTER_ATTRIBUTE_KEY, counter, ID_COUNTER_ATTRIBUTE_SCOPE);
        return FALLBACK_ID_PREFIX + counter;
    }

    private void addBodyToPending(String fallbackId, JspContext jspContext) {
        @SuppressWarnings("unchecked")
        LinkedHashMap<String, JspFragment> deferreds = (LinkedHashMap<String, JspFragment>)
                jspContext.getAttribute(PENDING_ATTRIBUTE_KEY, PENDING_ATTRIBUTE_SCOPE);
        if (deferreds == null) {
            deferreds = new LinkedHashMap<>();
            jspContext.setAttribute(PENDING_ATTRIBUTE_KEY, deferreds, PENDING_ATTRIBUTE_SCOPE);
        }
        deferreds.put(fallbackId, getJspBody());
    }

    static LinkedHashMap<String, JspFragment> getAndClearPendingItems(JspContext jspContext) {
        @SuppressWarnings("unchecked")
        LinkedHashMap<String, JspFragment> result = (LinkedHashMap<String, JspFragment>)
                jspContext.getAttribute(PENDING_ATTRIBUTE_KEY, PENDING_ATTRIBUTE_SCOPE);
        if (result != null) {
            jspContext.removeAttribute(PENDING_ATTRIBUTE_KEY, PENDING_ATTRIBUTE_SCOPE);
        }
        return result;
    }

}

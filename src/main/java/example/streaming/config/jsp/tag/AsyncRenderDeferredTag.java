package example.streaming.config.jsp.tag;

import static example.streaming.config.jsp.tag.AsyncDeferTag.*;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.JspFragment;

public class AsyncRenderDeferredTag extends RenderDeferredTag {

    @Override
    public void doTag() throws JspException, IOException {
        JspContext jspContext = getJspContext();
        JspWriter out = jspContext.getOut();

        Iterator<Map.Entry<String, JspFragment>> it = getAndConsumePending(jspContext);
        while (it.hasNext()) {
            do {
                out.flush(); // flush before blocking due to accessing iterator item
                render(it.next(), out);
            } while (it.hasNext());

            // Probably not necessary, but ensure new ones added due to nesting aren't missed.
            it = getAndConsumePending(jspContext);
        }
    }

    @Override
    protected String getEndDataMarker() {
        return END_DATA;
    }

}

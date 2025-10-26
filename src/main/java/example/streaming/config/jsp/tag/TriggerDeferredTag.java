package example.streaming.config.jsp.tag;

import static example.streaming.config.jsp.tag.DeferredTag.*;
import static org.w3c.dom.Node.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.JspFragment;
import javax.servlet.jsp.tagext.SimpleTagSupport;

public class TriggerDeferredTag extends SimpleTagSupport {

    @Override
    public void doTag() throws JspException, IOException {
        JspContext jspContext = getJspContext();
        JspWriter out = jspContext.getOut();

        LinkedHashMap<String, JspFragment> deferredMap = getAndClearPendingItems(jspContext);
        while (deferredMap != null && !deferredMap.isEmpty()) {
            for (Map.Entry<String, JspFragment> deferred : deferredMap.entrySet()) {
                out.flush(); // Rendering may block, so send buffered HTML to client first.

                StringWriter writer = new StringWriter();
                deferred.getValue().invoke(writer);

                StringBuilder builder = new StringBuilder();
                builder.append("<template>").append(writer).append("</template>");
                builder.append("<script>(() => {");
                appendJavaScript(builder, deferred.getKey());
                builder.append("})();</script>");

                out.write(builder.toString());
            }
            deferredMap = getAndClearPendingItems(jspContext); // May be new ones due to nesting
        }
    }

    // Replace the fallback with the real content.
    // Expect fallback to look something like:
    // <!--JD$--><template id="fbId"></template><div>1</div><div>2</div><!--/JD$-->
    // (with the template always empty and the fallback's nodes following it)
    private static void appendJavaScript(StringBuilder builder, String fallbackId) {
        builder.append("const self = document.currentScript;");
        builder.append("const contentNode = self.previousSibling;"); // template
        builder.append("contentNode.remove();"); // Detach from DOM tree, but keep a reference.

        builder.append("const fb = document.getElementById('").append(fallbackId).append("');");
        builder.append("if (!fb) return;");
        builder.append("const fbParent = fb.parentNode;");
        // @formatter:off
        builder.append("let node = fb.previousSibling;"); // Start from opening comment
        builder.append("do {");
        builder.append(    "if (node.nodeType === ").append(COMMENT_NODE);
        builder.append(            " && node.data === '").append(END_DATA).append("') {");
        builder.append(        "break;");
        builder.append(    "}");
        builder.append(    "const nextNode = node.nextSibling;");
        builder.append(    "fbParent.removeChild(node);"); // Gradually clear fallback
        builder.append(    "node = nextNode;");
        builder.append("} while (node);");
        // @formatter:on

        builder.append("fbParent.replaceChild(contentNode.content, node);"); // Replace end comment
        builder.append("self.remove();");
    }
}

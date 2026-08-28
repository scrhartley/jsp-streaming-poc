package example.streaming.config.jsp.tag;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;

// This implementation assumes the browser supports Declarative Shadow DOM.
// Support exists since Web Platform Baseline 2024: https://webstatus.dev/features/declarative-shadow-dom
// Note: if the slots' dependencies are not specified, then normal dynamic slots are rendered
// in the order they are registered, rather than the order they become ready.
class MultiSuspendDsdRenderer extends MultiSuspend.Renderer {

    private static final String SLOT_PREFIX = "s";
    private static final String LOAD_SLOT_PREFIX = "load_s";

    MultiSuspendDsdRenderer(List<MultiSuspendSlot> slots, List<String> staticContentSlots) {
        super(slots, staticContentSlots);
    }

    @Override
    public void render(JspWriter out, JspContext context) throws JspException, IOException {
        out.write("<div>"); // shadow host

        // Things we can render immediately.
        renderTemplate(out);
        renderPlaceholders(out);
        renderStaticContent(out);

        // Things which may require some waiting.
        renderDynamicContent(out, context);

        out.write("</div>");
    }

    private void renderTemplate(JspWriter out) throws IOException {
        out.write("<template shadowrootmode=\"open\">");
        out.write("<style>:host, ::slotted(*) { display: contents }</style>");
        for (ListIterator<MultiSuspendSlot> it = slots.listIterator(); it.hasNext();) {
            MultiSuspendSlot slot = it.next();
            String index = Integer.toString(it.nextIndex());

            out.write("<slot name=\"");
            out.write(SLOT_PREFIX);
            out.write(index);
            out.write("\">");

            if (slot != null) { // No load slots for static content
                out.write("<slot name=\"");
                out.write(LOAD_SLOT_PREFIX);
                out.write(index);
                out.write("\"></slot>");
            }

            out.write("</slot>");
        }
        out.write("</template>");
    }

    private void renderPlaceholders(JspWriter out) throws JspException, IOException {
        for (ListIterator<MultiSuspendSlot> it = slots.listIterator(); it.hasNext();) {
            MultiSuspendSlot slot = it.next();
            if (slot == null) { // No load slots for static content
                continue;
            }

            out.write("<div slot=\"");
            out.write(LOAD_SLOT_PREFIX);
            out.write(Integer.toString(it.nextIndex()));
            out.write("\">");

            writeFallback(out, slot);

            out.write("</div>");
        }
    }

    private void renderStaticContent(JspWriter out) throws IOException {
        Iterator<String> staticIt = staticContentSlots.iterator();
        for (ListIterator<MultiSuspendSlot> it = slots.listIterator(); it.hasNext();) {
            MultiSuspendSlot slot = it.next();
            if (slot == null) {
                out.write("<div slot=\"");
                out.write(SLOT_PREFIX);
                out.write(Integer.toString(it.nextIndex()));
                out.write("\">");
                out.write(staticIt.next());
                out.write("</div>");
            }
        }
    }

    private void renderDynamicContent(JspWriter out, JspContext context) throws JspException, IOException {
        Map<MultiSuspendSlot, Integer> numbered = new HashMap<>();
        for (ListIterator<MultiSuspendSlot> it = slots.listIterator(); it.hasNext();) {
            MultiSuspendSlot slot = it.next();
            if (slot != null) { // Static slots are rendered separately
                numbered.put(slot, it.nextIndex());
            }
        }

        Iterator<MultiSuspendSlot> it = MultiSuspendSlot.byDependencyReadiness(numbered.keySet(), context);
        while (it.hasNext()) {
            out.flush(); // prior to waiting

            MultiSuspendSlot slot = it.next();
            {
                StringWriter writer = new StringWriter();

                writer.getBuffer()
                        .append("<div slot=\"").append(SLOT_PREFIX).append(numbered.get(slot)).append("\">");
                slot.getJspBody().invoke(writer);
                writer.write("</div>");

                out.write(writer.toString());
            }
        }
    }

}

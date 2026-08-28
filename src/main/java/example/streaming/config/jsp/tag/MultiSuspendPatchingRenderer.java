package example.streaming.config.jsp.tag;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;

// This implementation assumes the browser supports the template element's "for" attribute (out-of-order patching);
// see: https://webstatus.dev/features/template-for
// Note: if the slots' dependencies are not specified, then normal dynamic slots are rendered
// in the order they are registered, rather than the order they become ready.
class MultiSuspendPatchingRenderer extends MultiSuspend.Renderer {

    private static final String MARKER_NAME_PREFIX = "msm";
    private static final char MARKER_NAME_SEPARATOR = '-';
    private static final int MARKER_START = 1;
    private static final int DISTINGUISHER_START = 1;
    private static final String DISTINGUISHER_CONTEXT_ATTR =
            MultiSuspendPatchingRenderer.class.getName().toLowerCase() + ".distinguisher";

    MultiSuspendPatchingRenderer(List<MultiSuspendSlot> slots, List<String> staticContentSlots) {
        super(slots, staticContentSlots);
    }

    @Override
    void render(JspWriter out, JspContext context) throws JspException, IOException {
        int distinguisher = getNextDistinguisher(context);
        renderMarkersAndStaticContent(out, distinguisher);
        renderTemplates(out, distinguisher, context);
    }

    private void renderMarkersAndStaticContent(JspWriter out, int distinguisher) throws JspException, IOException {
        int markerNumber = MARKER_START;
        Iterator<String> staticIt = staticContentSlots.iterator();
        for (MultiSuspendSlot slot : slots) {
            if (slot != null) {
                out.write("<?start name=\"");
                writeMarkerName(out, markerNumber++, distinguisher);
                out.write("\">");

                writeFallback(out, slot);

                out.write("<?end>");
            } else {
                out.write(staticIt.next());
            }
        }
    }

    private void renderTemplates(
            JspWriter out, int distinguisher, JspContext context) throws IOException, JspException {
        Map<MultiSuspendSlot, Integer> numbered = new HashMap<>();
        int markerNumber = MARKER_START;
        for (MultiSuspendSlot slot : slots) {
            if (slot != null) { // Static slots are rendered separately
                numbered.put(slot, markerNumber++);
            }
        }

        Iterator<MultiSuspendSlot> it = MultiSuspendSlot.byDependencyReadiness(numbered.keySet(), context);
        while (it.hasNext()) {
            out.flush(); // prior to waiting

            MultiSuspendSlot slot = it.next();
            {
                StringWriter writer = new StringWriter();

                writer.write("<template for=\"");
                writeMarkerName(writer, numbered.get(slot), distinguisher);
                writer.write("\">");

                slot.getJspBody().invoke(writer);

                writer.write("</template>");

                out.write(writer.toString());
            }
        }
    }

    private static void writeMarkerName(Writer writer, int markerNumber, int distinguisher) throws IOException {
        writer.write(MARKER_NAME_PREFIX);
        writer.write(MARKER_NAME_SEPARATOR);
        writer.write(Integer.toString(distinguisher));
        writer.write(MARKER_NAME_SEPARATOR);
        writer.write(Integer.toString(markerNumber));
    }

    // Distinguish between multiple MultiSuspend in a single web page (request).
    private static int getNextDistinguisher(JspContext context) {
        Integer attr = (Integer) context.getAttribute(DISTINGUISHER_CONTEXT_ATTR, PageContext.REQUEST_SCOPE);
        attr = (attr == null) ? DISTINGUISHER_START : attr + 1;
        context.setAttribute(DISTINGUISHER_CONTEXT_ATTR, attr, PageContext.REQUEST_SCOPE);
        return attr;
    }

}

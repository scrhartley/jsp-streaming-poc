package example.streaming.config.jsp.tag;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.JspFragment;

// This implementation uses Declarative Shadow DOM.
// If the browser doesn't support it, then the content will appear without showing placeholders/fallbacks first.
// Note: slots are rendered in the order they are registered, not the order they might become ready:
//       in ordered to guarantee this and hence be consistent cross-browser, dependencies are not supported.
//
// This renderer has the additional downside of static content (content not in explicit slots)
// being output twice: once each for the browser supporting DSD or not.
class MultiSuspendDsdCompatibilityRenderer extends MultiSuspend.Renderer {

    private static final String SLOT_PREFIX = "s";
    private static final String LOAD_SLOT_PREFIX = "load_s";
    private static final String DUMMY_STATIC_SLOT = "static";

    MultiSuspendDsdCompatibilityRenderer(List<MultiSuspendSlot> slots, List<String> staticContentSlots) {
        super(slots, staticContentSlots);
    }

    @Override
    public void render(JspWriter out, JspContext context) throws JspException, IOException {
        if (slots.stream()
                .filter(Objects::nonNull)
                .anyMatch(MultiSuspendSlot::hasDependencies)) {
            throw new IllegalStateException("This renderer does not support using dependencies.");
        }

        out.write("<div style=\"display:contents\">"); // shadow host
        renderTemplate(out);
        renderPlaceholders(out);
        renderContent(out);
        out.write("</div>");
    }

    private void renderTemplate(JspWriter out) throws IOException {
        out.write("<template shadowrootmode=\"open\">");
        out.write("<style>::slotted([hidden]) { display: contents }</style>");
        for (ListIterator<MultiSuspendSlot> it = slots.listIterator(); it.hasNext();) {
            MultiSuspendSlot slot = it.next();
            String index = Integer.toString(it.nextIndex());

            out.write("<slot name=\"");
            out.write(SLOT_PREFIX);
            out.write(index);
            out.write("\">");

            if (slot != null) {
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
        Iterator<String> staticIt = staticContentSlots.iterator();
        for (ListIterator<MultiSuspendSlot> it = slots.listIterator(); it.hasNext();) {
            MultiSuspendSlot slot = it.next();

            out.write("<div slot=\"");
            out.write(slot != null ? LOAD_SLOT_PREFIX : SLOT_PREFIX);
            out.write(Integer.toString(it.nextIndex()));
            out.write("\" hidden>"); // hidden in case no DSD support

            if (slot != null) { // Dynamic content placeholder
                JspFragment fallbackFragment = slot.getFallbackFragment();
                if (fallbackFragment != null) {
                    fallbackFragment.invoke(out);
                } else {
                    String fallback = slot.getFallback();
                    if (fallback == null) {
                        fallback = "<div>Loading ...</div>";
                    }
                    out.write(fallback);
                }
            } else { // The actual slot for static content.
                out.write(staticIt.next());
            }

            out.write("</div>");
        }
    }

    private void renderContent(JspWriter out) throws JspException, IOException {
        Iterator<String> staticIt = staticContentSlots.iterator();
        for (ListIterator<MultiSuspendSlot> it = slots.listIterator(); it.hasNext();) {
            MultiSuspendSlot slot = it.next();

            if (slot != null) {
                out.flush(); // prior to waiting
            }

            StringWriter writer = new StringWriter();

            writer.write("<div slot=\"");
            if (slot != null) {
                writer.write(SLOT_PREFIX);
                writer.write(String.valueOf(it.nextIndex()));
            } else {
                // This is just here to simplify the CSS used by the client,
                // so that everything appears as if it's in a real slot.
                writer.write(DUMMY_STATIC_SLOT);
            }
            writer.write("\" style=\"display:contents\">");

            if (slot != null) {
                slot.getJspBody().invoke(writer);
            } else {
                // Here for if the browser doesn't support DSD.
                // It's not in a real slot, so not rendered in shadow host if DSD is supported.
                writer.write(staticIt.next());
            }
            writer.write("</div>");

            out.write(writer.toString());
        }
    }
}

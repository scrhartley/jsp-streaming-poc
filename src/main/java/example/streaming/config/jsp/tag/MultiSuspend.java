package example.streaming.config.jsp.tag;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.tagext.JspFragment;
import javax.servlet.jsp.tagext.SimpleTagSupport;

public class MultiSuspend extends SimpleTagSupport {

    // For browsers that might not support declarative shadow dom
    private static final boolean COMPATIBILITY_MODE = false;

    private final List<MultiSuspendSlot> slots = new ArrayList<>();
    private final List<String> staticContentSlots = new ArrayList<>();
    private final StringWriter contentCatcher = new StringWriter();

    @Override
    public void doTag() throws JspException, IOException {
        JspFragment body = getJspBody();
        if (body == null) {
            throw new JspException("This tag is not intended to be empty.");
        }

        body.invoke(contentCatcher); // get slots to register themselves
        tryCaptureStaticContent(); // Content after last slot (or if no slots)

        JspContext context = getJspContext();
        JspWriter out = context.getOut();
        if (slots.stream().allMatch(Objects::isNull)) { // static content only
            for (String staticContent : this.staticContentSlots) {
                out.write(staticContent);
            }
        } else {
            Renderer renderer = COMPATIBILITY_MODE
                    ? new MultiSuspendDsdCompatibilityRenderer(slots, staticContentSlots)
                    : new MultiSuspendDsdRenderer(slots, staticContentSlots);
            renderer.render(out, context);
        }
    }

    void registerSlot(MultiSuspendSlot slot) {
        tryCaptureStaticContent();
        slots.add(slot);
    }

    private void tryCaptureStaticContent() {
        String staticContent = contentCatcher.getBuffer().toString().trim();
        if (!staticContent.isEmpty()) {
            slots.add(null);
            staticContentSlots.add(staticContent);
        }
        contentCatcher.getBuffer().setLength(0); // Clear
    }

    static abstract class Renderer {
        private static final String DEFAULT_FALLBACK = "<div>Loading ...</div>";

        protected final List<MultiSuspendSlot> slots;
        protected final List<String> staticContentSlots;

        Renderer(List<MultiSuspendSlot> slots, List<String> staticContentSlots) {
            this.slots = slots;
            this.staticContentSlots = staticContentSlots;
        }

        abstract void render(JspWriter out, JspContext context) throws JspException, IOException;


        void writeFallback(Writer out, MultiSuspendSlot slot) throws JspException, IOException {
            JspFragment fallbackFragment = slot.getFallbackFragment();
            if (fallbackFragment != null) {
                fallbackFragment.invoke(out);
            } else {
                String fallback = slot.getFallback();
                out.write((fallback != null) ? fallback : DEFAULT_FALLBACK);
            }
        }
    }

}

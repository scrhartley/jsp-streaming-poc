package example.streaming.config.jsp.tag;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.JspFragment;
import javax.servlet.jsp.tagext.JspTag;
import javax.servlet.jsp.tagext.SimpleTagSupport;

import example.streaming.config.mvc.FutureUpgrader;

public class MultiSuspendSlot extends SimpleTagSupport {

    private static final Pattern DEPENDENCIES_SPLIT = Pattern.compile(" *, *");

    private String fallback;
    private JspFragment fallbackFragment;
    private Set<String> dependencies = Collections.emptySet();

    @Override
    public void doTag() throws JspException {
        if (fallback != null && fallbackFragment != null) {
            throw new JspTagException("Only use one of fallback and fallbackFragment");
        }
        if (getJspBody() != null) {
            getContainer().registerSlot(this);
        }
    }

    private MultiSuspend getContainer() throws JspException {
        JspTag parent = getParent();
        if (parent instanceof MultiSuspend) {
            return (MultiSuspend) parent;
        } else {
            throw new JspException("This tag is not intended to be used by itself.");
        }
    }

    // Make available to package
    @Override
    protected JspFragment getJspBody() {
        return super.getJspBody();
    }


    String getFallback() {
        return fallback;
    }
    public void setFallback(String fallback) {
        this.fallback = fallback;
    }

    JspFragment getFallbackFragment() {
        return fallbackFragment;
    }
    public void setFallbackFragment(JspFragment fallback) {
        this.fallbackFragment = fallback;
    }

    boolean hasDependencies() {
        return !dependencies.isEmpty();
    }
    public void setDependencies(String deps) throws JspException {
        dependencies = DEPENDENCIES_SPLIT
                .splitAsStream(deps.trim())
                .collect(Collectors.toSet());

        Set<String> allowed = futuresState(getJspContext()).getAttributeNames();
        for (String dependency : dependencies) {
            if (!allowed.contains(dependency)) {
                throw new JspException("Unknown or unsupported dependency: " + dependency);
            }
        }
    }

    private static FutureUpgrader.FutureUpgraderResult futuresState(JspContext context) {
        return (FutureUpgrader.FutureUpgraderResult) context
                .getAttribute(FutureUpgrader.FutureUpgraderResult.KEY, PageContext.REQUEST_SCOPE);
    }

    static Iterator<MultiSuspendSlot> byDependencyReadiness(Collection<MultiSuspendSlot> pendingSlots, JspContext context) {
        pendingSlots.forEach(Objects::requireNonNull);

        if (pendingSlots.stream().noneMatch(MultiSuspendSlot::hasDependencies)) {
            return pendingSlots.iterator();
        }

        return new Iterator<>() {
            final Iterator<Collection<String>> queue = futuresState(context).completionIterator();
            final Set<MultiSuspendSlot> slots = new HashSet<>(pendingSlots);

            @Override
            public boolean hasNext() {
                return !slots.isEmpty() && (findReady().isPresent() || queue.hasNext());
            }

            @Override
            public MultiSuspendSlot next() {
                MultiSuspendSlot ready;

                // We only return one slot at a time but one queue
                // dependency may cause multiple slots to be ready.
                if ((ready = nextReady()) != null) {
                    return ready;
                }

                while (queue.hasNext()) {
                    Collection<String> resolved = queue.next();
                    for (MultiSuspendSlot slot : slots) {
                        slot.dependencies.removeAll(resolved);
                    }
                    if ((ready = nextReady()) != null) {
                        return ready;
                    }
                }

                throw new IllegalStateException("Problem processing pending slots queue");
            }

            private Optional<MultiSuspendSlot> findReady() {
                return slots.stream()
                        .filter(slot -> slot.dependencies.isEmpty())
                        .findAny();
            }

            private MultiSuspendSlot nextReady() {
                return findReady()
                        .map(slot -> {
                            slots.remove(slot);
                            return slot;
                        })
                        .orElse(null);
            }
        };
    }

}

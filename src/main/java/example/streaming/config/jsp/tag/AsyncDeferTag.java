package example.streaming.config.jsp.tag;

import static example.streaming.config.mvc.FutureUpgrader.*;

import java.io.IOException;
import java.io.StringWriter;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.servlet.jsp.JspContext;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.JspFragment;
import javax.servlet.jsp.tagext.SimpleTagSupport;

public class AsyncDeferTag extends SimpleTagSupport {

    private static final String SHARED_FALLBACK_NAME = "sharedDeferFallback";
    private static final int SHARED_FALLBACK_SCOPE = PageContext.REQUEST_SCOPE;
    private static final String ID_COUNTER_ATTRIBUTE_KEY = "_}\n$%___ASYNC_DEFERRED_ID___%$\n{_";
    private static final int ID_COUNTER_ATTRIBUTE_SCOPE = PageContext.REQUEST_SCOPE;
    private static final String PENDING_ATTRIBUTE_KEY = "_}\n$%___ASYNC_DEFERRED_PENDING___%$\n{_";
    private static final int PENDING_ATTRIBUTE_SCOPE = PageContext.REQUEST_SCOPE;
    private static final String FALLBACK_ID_PREFIX = "AD_fb:";
    static final String START_DATA = "AD$";
    static final String END_DATA = "/AD$";

    private String fallback;
    private JspFragment fallbackFragment;
    private Set<String> dependencies;

    private static final Pattern DEPENDENCIES_SPLIT = Pattern.compile(" *, *");

    public void setFallback(String fallback) {
        this.fallback = fallback;
    }
    public void setFallbackFragment(JspFragment fallback) {
        this.fallbackFragment = fallback;
    }
    public void setDependencies(String deps) {
        this.dependencies = DEPENDENCIES_SPLIT
                .splitAsStream(deps.trim())
                .collect(Collectors.toSet());
    }

    @Override
    public void doTag() throws JspException, IOException {
        if (fallback != null && fallbackFragment != null) {
            throw new JspTagException("Only use one of fallback and fallbackFragment");
        }
        JspContext jspContext = getJspContext();

        checkDependencies(jspContext);

        JspWriter out = jspContext.getOut();
        String fallbackId = getNextFallbackId(jspContext);
        if (fallback != null) {
            out.write(buildFallbackContent(fallback, fallbackId));
            addToPending(fallbackId, jspContext);
        } else if (fallbackFragment != null) {
            StringWriter temp = new StringWriter();
            fallbackFragment.invoke(temp);
            out.write(buildFallbackContent(temp, fallbackId));
            addToPending(fallbackId, jspContext);
        } else {
            Object defaultFallback = jspContext.getAttribute(SHARED_FALLBACK_NAME, SHARED_FALLBACK_SCOPE);
            out.write(buildFallbackContent(defaultFallback, fallbackId));
            addToPending(fallbackId, jspContext);
        }
    }

    private static String buildFallbackContent(Object fallback, String id) {
        return "<!--" + START_DATA + "-->" +
                "<template id=\"" + id + "\"></template>" + fallback +
                "<!--" + END_DATA + "-->";
    }

    private void checkDependencies(JspContext context) throws JspException {
        Set<String> allowedAttributes = getFuturesState(context).getAttributeNames();

        for (String dependency : this.dependencies) {
            if (!allowedAttributes.contains(dependency)) {
                throw new JspException("Unknown or unsupported dependency: " + dependency);
            }
        }
    }

    private static FutureUpgraderResult getFuturesState(JspContext context) {
        return (FutureUpgraderResult) context
                .getAttribute(FutureUpgraderResult.KEY, PageContext.REQUEST_SCOPE);
    }


    private static String getNextFallbackId(JspContext context) {
        Integer counter = (Integer) context
                .getAttribute(ID_COUNTER_ATTRIBUTE_KEY, ID_COUNTER_ATTRIBUTE_SCOPE);
        counter = counter==null ? 1 : counter + 1;
        context.setAttribute(ID_COUNTER_ATTRIBUTE_KEY, counter, ID_COUNTER_ATTRIBUTE_SCOPE);
        return FALLBACK_ID_PREFIX + counter;
    }

    private void addToPending(String fallbackId, JspContext context) {
        @SuppressWarnings("unchecked")
        Set<PendingItem> deferreds = (Set<PendingItem>)
                context.getAttribute(PENDING_ATTRIBUTE_KEY, PENDING_ATTRIBUTE_SCOPE);
        if (deferreds == null) {
            deferreds = new HashSet<>();
            context.setAttribute(PENDING_ATTRIBUTE_KEY, deferreds, PENDING_ATTRIBUTE_SCOPE);
        }
        deferreds.add(new PendingItem(dependencies, getJspBody(), fallbackId));
    }

    private static class PendingItem {
        final Set<String> dependencies;
        final JspFragment fragment;
        final String id;

        public PendingItem(Set<String> dependencies, JspFragment fragment, String id) {
            this.dependencies = dependencies;
            this.fragment = fragment;
            this.id = id;
        }
    }

    static Iterator<Map.Entry<String, JspFragment>> getAndConsumePending(JspContext context) {
        @SuppressWarnings("unchecked")
        Set<PendingItem> pendingItems = (Set<PendingItem>)
                context.getAttribute(PENDING_ATTRIBUTE_KEY, PENDING_ATTRIBUTE_SCOPE);
        if (pendingItems == null || pendingItems.isEmpty()) {
            return Collections.emptyIterator();
        }

        return new Iterator<>() {
            final Iterator<String> queue = getFuturesState(context).getCompletionQueue().iterator();
            final Set<String> allResolved = new HashSet<>();
            int expectedPendingCount = pendingItems.size();

            @Override
            public boolean hasNext() {
                return !pendingItems.isEmpty() && (queue.hasNext() || findReady().isPresent());
            }

            @Override
            public Map.Entry<String, JspFragment> next() {
                Map.Entry<String, JspFragment> ready;

                // We only return one item at a time but one queue
                // dependency may cause multiple pending to be ready.
                if ((ready = nextReady()) != null) {
                    return ready;
                }

                // The caller of this method may generate a new pending item which
                // depends upon something previously returned from the queue.
                // So we need to update any new pending with those and potentially return one.
                if (expectedPendingCount != pendingItems.size()) {
                    expectedPendingCount = pendingItems.size();
                    for (PendingItem item : pendingItems) {
                        item.dependencies.removeAll(allResolved);
                    }
                    if ((ready = nextReady()) != null) {
                        return ready;
                    }
                }

                while (queue.hasNext()) {
                    String resolved = queue.next();
                    allResolved.add(resolved);
                    for (PendingItem item : pendingItems) {
                        item.dependencies.remove(resolved);
                    }
                    if ((ready = nextReady()) != null) {
                        return ready;
                    }
                }

                throw new IllegalStateException("Problem processing pending items queue");
            }

            private Optional<PendingItem> findReady() {
                return pendingItems.stream()
                        .filter(item -> item.dependencies.isEmpty())
                        .findAny();
            }

            private Map.Entry<String, JspFragment> nextReady() {
                return findReady()
                        .map(item -> {
                            pendingItems.remove(item);
                            expectedPendingCount--;
                            return new SimpleImmutableEntry<>(item.id, item.fragment);
                        })
                        .orElse(null);
            }
        };
    }

}

<%@ tag %> <%-- Inspiration https://react.dev/reference/react/Suspense --%>
<%@ attribute name="fallback" %>
<%@ attribute name="fallbackFragment" fragment="true" %>
<%@ taglib prefix="c"   uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="tag" tagdir="/WEB-INF/tags" %>
<c:set var="__suspenseIdCounter__" scope="request" value="${requestScope.__suspenseIdCounter__ + 1}" />
<c:set var="id" value="JSUSP_fallback:${requestScope.__suspenseIdCounter__}" />
<c:choose>
    <c:when test="${fallbackFragment != null}"><template id="${id}"><jsp:invoke fragment="fallbackFragment" /></template></c:when>
    <c:when test="${fallback != null}"><template id="${id}">${fallback}</template></c:when>
    <c:otherwise><template id="${id}">${requestScope.sharedSuspenseFallback}</template></c:otherwise>
</c:choose>
<%-- Show fallback using JS so it will not show if no JS. --%>
<script>(t => t.after(t.content))(document.getElementById("${id}"));</script>
<tag:atom>
        <%-- Remove fallback --%>
        <script>(() => {
            const self = document.currentScript;
            let el = document.getElementById("${id}");
            do {
                let next = el.nextSibling;
                el.parentNode.removeChild(el);
                el = next;
            } while (el && el !== self);
            self.remove();
        })();</script>

        <jsp:doBody />
</tag:atom>
<%@ tag trimDirectiveWhitespaces="true" %> <%-- Inspiration https://react.dev/reference/react/Suspense --%>
<%@ attribute name="fallback" %>
<%@ attribute name="fallbackFragment" fragment="true" %>
<%@ taglib prefix="c"   uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="tag" tagdir="/WEB-INF/tags" %>

<div style="display:contents"><template shadowrootmode="open"><slot><c:choose>
    <c:when test="${fallbackFragment != null}"><jsp:invoke fragment="fallbackFragment" /></c:when>
    <c:when test="${fallback != null}">${fallback}</c:when>
    <c:otherwise>${requestScope.sharedSuspendFallback}</c:otherwise>
</c:choose></slot></template><tag:atom><jsp:doBody /></tag:atom></div>
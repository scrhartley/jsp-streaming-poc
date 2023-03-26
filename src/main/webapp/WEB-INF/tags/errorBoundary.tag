<%@ tag %> <%-- Inspiration: https://react.dev/reference/react/Component#catching-rendering-errors-with-an-error-boundary --%>
<%@ attribute name="fallback" %>
<%@ attribute name="fallbackFragment" fragment="true" %>
<%@ taglib prefix="c"   uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="tag" tagdir="/WEB-INF/tags" %>
<c:catch var="bodyError"><tag:atom><jsp:doBody /></tag:atom></c:catch>
<c:if test="${bodyError != null}">
    ${bodyError.printStackTrace()} <%-- Perhaps replace with your own logging in FutureELResolver. --%>
    <c:choose>
        <c:when test="${fallbackFragment != null}"><jsp:invoke fragment="fallbackFragment" /></c:when>
        <c:when test="${fallback != null}">${fallback}</c:when>
        <c:when test="${requestScope.sharedErrorFallback != null}">${requestScope.sharedErrorFallback}</c:when>
        <c:otherwise>
            <%--
                Alternatively we could throw using the following
                (as well as adding a workaround for an unreachable code compilation error
                 by making sure this scriptlet is the very last thing in the file):

                <% throw (Exception) jspContext.getAttribute("bodyError"); %>
            --%>
            <div style="border: 1px solid red">
                 Generic error fallback
            </div>
        </c:otherwise>
    </c:choose>
</c:if>
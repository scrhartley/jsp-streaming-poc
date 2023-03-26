<%@ taglib prefix="tag" tagdir="/WEB-INF/tags" %>
<%@ taglib prefix="c"   uri="http://java.sun.com/jsp/jstl/core" %>

<c:set var="myLoadingFallback">
    Loading (with manually passed fallback) ...
</c:set>

<c:set var="sharedSuspenseFallback" scope="request">
    Loading (with request scope implicit fallback) ...
</c:set>

<!DOCTYPE html>
<html>
	<head>
	    <style> li + li { padding-top: 0.5rem; } </style>
	</head>
	<body>

	    <ul>
            <li> <div>My page using suspense tag!</div> </li>

            <li>
                <tag:suspense> <div>${myData1}</div> </tag:suspense>
            </li>
            <li>
                <tag:suspense fallback="${myLoadingFallback}"> <div>${myData2}</div> </tag:suspense>
            </li>
            <li>
                <tag:suspense fallback="Loading (with HTML string fallback) ..."> <div>${myData3}</div> </tag:suspense>
            </li>
            <li>
                <tag:suspense>
                    <jsp:attribute name="fallbackFragment">
                        Loading (with fragment fallback) ...
                    </jsp:attribute>
                    <jsp:body> <div>${myData4}</div> </jsp:body>
                </tag:suspense>
            </li>
        </ul>

        <div>Page finished!</div>

	</body>
</html>
<%@ taglib prefix="tag" uri="custom.tags.experimental" %>
<%@ taglib prefix="c"   uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="atom" tagdir="/WEB-INF/tags" %>
<c:set var="myLoadingFallback">
    Loading (with manually passed fallback) ...
</c:set>
<c:set var="sharedDeferredFallback" scope="request">
    Loading (with request scope implicit fallback) ...
</c:set>

<!DOCTYPE html>
<html>
	<head>
        <style>
            li + li { padding-top: 0.5rem; }
            .box { width: 25%; border: 1px solid black; padding: 0.5em; }
            .box .box { width: 80%; border-color: grey; margin-top: 0.5em; }
            .box .box .box { border-color: blue; }
         </style>
	</head>
	<body>
	    <ul>
            <li> <div>My page using deferred tag!</div> </li>

            <li>
                <tag:deferred> <div>${myData1}</div> </tag:deferred>
            </li>
            <li>
                <tag:deferred fallback="${myLoadingFallback}"> <div>${myData2}</div> </tag:deferred>
            </li>
            <li>
                <tag:deferred>
                    <jsp:attribute name="fallbackFragment">
                        Loading (with fragment fallback) ...
                    </jsp:attribute>
                    <jsp:body> <div>${myData3}</div> </jsp:body>
                </tag:deferred>
            </li>

            <li>
                <tag:deferred fallback="Loading (with HTML string fallback) ...">
                    <div class="box">

                        <div>${myData4}</div>

                        <div class="box">
                            <tag:deferred fallback="Loading (nested) ...">
                                <div>${myData5}</div>
                            </tag:deferred>

                            <div class="box">
                                <tag:deferred fallback="Loading (nested 2) ...">
                                    ${myData6}
                                </tag:deferred>
                            </div>
                        </div>

                    </div>
                </tag:deferred>
            </li>
        </ul>

        <div>Page finished!</div>
        <tag:triggerDeferred />
    </body>
</html>
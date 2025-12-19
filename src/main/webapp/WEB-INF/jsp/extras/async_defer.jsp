<%@ taglib prefix="tag" uri="custom.tags.experimental" %>
<%@ taglib prefix="c"   uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="myLoadingFallback">
    Loading (with manually passed fallback) ...
</c:set>
<c:set var="sharedDeferFallback" scope="request">
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
            <li> <div>My page using asyncDefer tag!</div> </li>

            <li>
                <tag:asyncDefer dependencies="myData1"> <div>${myData1}</div> </tag:asyncDefer>
            </li>
            <li>
                <tag:asyncDefer dependencies="myData2" fallback="${myLoadingFallback}">
                    <div>${myData2}</div>
                </tag:asyncDefer>
            </li>
            <li>
                <tag:asyncDefer dependencies="myData3">
                    <jsp:attribute name="fallbackFragment">
                        Loading (with fragment fallback) ...
                    </jsp:attribute>
                    <jsp:body> <div>${myData3}</div> </jsp:body>
                </tag:asyncDefer>
            </li>

            <li>
                <tag:asyncDefer dependencies="myData4" fallback="Loading (with HTML string fallback) ... ">
                    <div class="box">

                        <div>${myData4}</div>

                        <div class="box">
                            <tag:asyncDefer dependencies="myData5" fallback="Loading (nested) ...">
                                <div>${myData5}</div>
                            </tag:asyncDefer>

                            <div class="box">
                                <tag:asyncDefer dependencies="myData6" fallback="Loading (nested 2) ...">
                                    ${myData6}
                                </tag:asyncDefer>
                            </div>
                        </div>

                    </div>
                </tag:asyncDefer>
            </li>

            <li>
                <tag:asyncDefer dependencies="subModel1" fallback="Loading sub-model">
                    <div>${subModel1.subValue1}</div>
                    <div>${subModel1.subValue2}</div>
                </tag:asyncDefer>
            </li>
        </ul>

        <div>Page finished!</div>
        <tag:renderAsyncDeferred />
    </body>
</html>
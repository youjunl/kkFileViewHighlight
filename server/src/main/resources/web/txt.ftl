<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, user-scalable=yes, initial-scale=1.0">
    <title>${file.name}普通文本预览</title>
    <#include "*/commonHeader.ftl">
    <script src="js/jquery-3.6.1.min.js" type="text/javascript"></script>
    <link rel="stylesheet" href="bootstrap/css/bootstrap.min.css"/>
    <script src="bootstrap/js/bootstrap.min.js" type="text/javascript"></script>
    <script src="js/base64.min.js" type="text/javascript"></script>
</head>
<body>
<input hidden id="textData" value="${textData}"/>
<#if "${file.suffix?html}" == "txt" || "${file.suffix?html}" == "log"  || "${file.suffix?html}" == "TXT"  || "${file.suffix?html}" == "LOG">
  <style type="text/css">
DIV.black{line-height:25px;PADDING-RIGHT:1px;PADDING-LEFT:1px;FONT-SIZE:100%;MARGIN:1px;COLOR:#000000;BACKGROUND-COLOR:#f5f5f5;TEXT-ALIGN:left}
DIV.black A{BORDER-RIGHT:#909090 1px solid;PADDING-RIGHT:5px;BACKGROUND-POSITION:50% bottom;BORDER-TOP:#909090 1px solid;PADDING-LEFT:5px;BACKGROUND-IMAGE:url();PADDING-BOTTOM:2px;BORDER-LEFT:#909090 1px solid;COLOR:#000000;MARGIN-RIGHT:3px;PADDING-TOP:2px;BORDER-BOTTOM:#909090 1px solid;TEXT-DECORATION:none}
DIV.black A:hover{BORDER-RIGHT:#f0f0f0 1px solid;BORDER-TOP:#f0f0f0 1px solid;BACKGROUND-IMAGE:BORDER-LEFT:#f0f0f0 1px solid;COLOR:#000000;BORDER-BOTTOM:#f0f0f0 1px solid;BACKGROUND-COLOR:#e0e0e0}
DIV.black A:active{BORDER-RIGHT:#f0f0f0 1px solid;BORDER-TOP:#f0f0f0 1px solid;BACKGROUND-IMAGE:BORDER-LEFT:#f0f0f0 1px solid;COLOR:#000000;BORDER-BOTTOM:#f0f0f0 1px solid;BACKGROUND-COLOR:#e0e0e0}
.divContent{
    color:#000000;
    font-size：30px;
    line-height：30px;
    font-family："SimHei";
    padding-bottom:10px;
    white-space:pre-wrap;
    white-space:-moz-pre-wrap;
    white-space:-pre-wrap;
    white-space:-o-pre-wrap;
    word-wrap:break-word;
    background-color:#f5f5f5
}
input{
    color:#000000;
    background-color:#f5f5f5
}
.highlight {
    background-color: #acc5df;
    font-weight: bold;
}
    </style>


	<div class="container">
    <div class="panel panel-default">
        <div class="panel-heading">
            <h4 class="panel-title">
                <a data-toggle="collapse" data-parent="#accordion" href="#collapseOne">
                    ${file.name}
                </a>
            </h4>
        </div>
        <div class="panel-body">
          <div id="divPagenation" class="black" >

    </div>
        <div id="divContent" class="panel-body">
           </div>
        </div>
    </div>
</div>
 <script type="text/javascript">
        var base64data = $("#textData").val();
        var s = Base64.decode(base64data);
        var keyword = decodeURIComponent("${keyword}");

        // 添加高亮关键词的函数
        function highlightKeyword(content, keyword) {
            if (!keyword) return content;

            // 先将文本内容转义为 HTML 实体
            content = content.replace(/&/g, '&amp;')
                           .replace(/</g, '&lt;')
                           .replace(/>/g, '&gt;')
                           .replace(/"/g, '&quot;')
                           .replace(/'/g, '&#039;');

            var re = new RegExp(keyword, 'gi');
            return content.replace(re, match => `<span class="highlight">${keyword}</span>`);
        }
        
        // 在 divContent 中显示高亮后的内容，保持原始格式
        $("#divContent").html('<pre>' + highlightKeyword(s, keyword) + '</pre>');

        // 滚动到高亮词位置
        function scrollToHighlight() {
            var highlightElement = document.querySelector('.highlight');
            if (highlightElement) {
                highlightElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        }

        // 页面加载完成后滚动到高亮词
        window.onload = function () {
            initWaterMark();
            scrollToHighlight();
        }

</script>
 <#else/>
<div class="container">
    <div class="panel panel-default">
        <div class="panel-heading">
            <h4 class="panel-title">
                <a data-toggle="collapse" data-parent="#accordion" href="#collapseOne">
                    ${file.name}
                </a>
            </h4>
        </div>
        <div class="panel-body">
            <div id="text"></div>
        </div>
    </div>
</div>

<script>


    function loadText() {
        var base64data = $("#textData").val()
        var textData = Base64.decode(base64data);
        // 添加关键词高亮
        var highlightedText = highlightKeyword(textData, keyword);
        var textPreData = "<xmp style='background-color: #FFFFFF;overflow-y: scroll;border:none'>" + highlightedText + "</xmp>";
        $("#text").append(textPreData);
    }
   /**
     * 初始化
     */
    window.onload = function () {
        initWaterMark();
        loadText();
    }
</script>
	 </#if>
</body>
</html>

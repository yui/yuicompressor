<?php

header( "Content-type: text/html; charset=UTF-8" );

define("DEFAULT_TEXT", "Paste your JavaScript code here...");

define("SUCCESS", 0);
define("UNEXPECTED_ERROR", 1);
define("SYNTAX_ERROR", 2);

$jssrc = isset($_POST["jssrc"]) ? $_POST["jssrc"] : "";

$err = -1;
$content = "";

if ( $jssrc ) {

    $filename = $_SERVER["PATH_TRANSLATED"];
    $idx = strrpos($filename, "/");
    $dir = substr($filename, 0, $idx+1);
    $input = $dir . uniqid("input") . ".js";
    $output = $dir . uniqid("output") . ".js";

    $fp = fopen($input, "w");
    fwrite($fp, $jssrc);
    fclose($fp);

    $cmd = "java -jar " . $dir . "yuicompressor-@VERSION.NUMBER@.jar --charset UTF-8 -o " . $output . " " . $input . " 2>&1";
    exec($cmd, $out, $err);
    unlink($input);

    if ($err === 0) {
        $fp = fopen($output, "r");
        $content = fread($fp, filesize($output));
        fclose($fp);
        unlink($output);
    } else if ($err === SYNTAX_ERROR) {
        $content = "The YUI Compressor reported the following error(s):\n\n" . implode("\n", $out);
    } else {
        $content = "An unexpected error occurred:\n\n" . implode("\n", $out);
    }

} else {
    $content = DEFAULT_TEXT;
}

?><!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN" "http://www.w3.org/TR/html4/strict.dtd">
<html>
  <head>
    <meta http-equiv="Content-Type" content="text/html;charset=UTF-8"/>
    <title>YUI JavaScript Compressor</title>
    <link rel="stylesheet" type="text/css" href="reset.css"/>
    <link rel="stylesheet" type="text/css" href="fonts.css"/>
    <style>

body {
  padding:10px;
}

#doc {
  width:750px;
  position:relative;
}

#pagetitle {
  background:#b6cde1 url(hdbg.png) repeat-x;
  border:1px solid #93b2cc;
}

#pagetitle h1 {
  font-size:1.2em;
  font-weight:bold;
  margin:3px;
}

#jssrc {
  width:742px; height:300px;
  margin-top:8px;
  background:#fff8ea;
  border:1px solid #93b2cc;
  padding:3px;
  font-family:courier new;
  overflow:auto;
  font-size:1em;
}

#jssrc.empty {
  color:#aaa;
}

#jssrc.error {
  color:#f00;
}

#jssrc.code {
  color:#000;
}

#ft {
  margin-top:8px;
}

.btn {
  font-size:1em;
  padding:.2em;
  *padding:.1em 0;
}
    </style>
  </head>
  <body>
    <form method="POST">
      <div id="doc">
        <div id="hd">
          <a href="http://www.yahoo.com"><img src="yahoo.gif" style="width:140px;height:33px;" alt="Yahoo!"></a>
          <div id="pagetitle"><h1>The YUI JavaScript Compressor</h1></div>
        </div>
        <div id="bd">
            <textarea id="jssrc" name="jssrc" spellcheck="false"><?php print $content; ?></textarea>
        </div>
        <div id="ft">
          <input type="submit" value="Compress" id="submitbtn" class="btn">
          <input type="reset" value="Clear" id="clearbtn" class="btn">
        </div>
      </div>
    </form>

    <script src="yahoo-dom-event.js"></script>
    <script>

( function () {

    var textarea = YAHOO.util.Dom.get("jssrc");

    function trim(s) {
        return s.replace(/^\s*(\S*(\s+\S+)*)\s*$/, "$1");
    }

<?php

if ($err === 0) {
    $className = "code";
} else if ($err > 0) {
    $className = "error";
} else {
    $className = "empty";
}

print '    textarea.className = "' . $className . '";';

?>

    YAHOO.util.Event.addListener(textarea, "focus", function (evt) {
        if (YAHOO.util.Dom.hasClass(this, "empty")) {
            this.className = "code";
            this.value = "";
        }
    } );

    YAHOO.util.Event.addListener(textarea, "blur", function (evt) {
        if (trim(this.value) === "") {
            this.className = "empty";
            this.value = "<?php print DEFAULT_TEXT; ?>";
        }
    } );

    YAHOO.util.Event.addListener("submitbtn", "click", function (evt) {
        if (YAHOO.util.Dom.hasClass(textarea, "empty")) {
            YAHOO.util.Event.stopEvent(evt);
        }
    } );

    YAHOO.util.Event.addListener("clearbtn", "click", function (evt) {
        textarea.value = "<?php print DEFAULT_TEXT; ?>";
        textarea.className = "empty";
        YAHOO.util.Event.stopEvent(evt);
    } );

} )();

    </script>
  </body>
</html>
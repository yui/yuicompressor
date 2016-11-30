<?php

require_once __DIR__ . "/../cssmin.php";

class YuiTest extends PHPUnit_Framework_TestCase {
    protected static $inputDir = "yuicompressor";

    public function setUp() {
        $this->cssmin = new \Yahoo\Compressor();
    }

    /**
     * @dataProvider fileNameProvider
     */
    public function testInputFile($inFname) {

        $outFname = $inFname . ".min";

        if (!is_readable($outFname)) {
            $this->fail("$outFname is missing");
        }

        $input = file_get_contents($inFname);
        $output = trim(file_get_contents($outFname));

        $this->assertEquals($output, $this->cssmin->cssmin($input, 0));
    }

    public function fileNameProvider() {
        return array_map(function($a) { return array($a); },
            self::findInputNames());
    }

    static public function findInputNames($pattern="*.css") {
        $files = glob(__DIR__ . "/../../../tests/" . $pattern);
        return array_filter($files, "is_file");
    }
}

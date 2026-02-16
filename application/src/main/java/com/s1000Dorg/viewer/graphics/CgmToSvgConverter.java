package com.s1000Dorg.viewer.graphics;

import java.io.IOException;
import java.io.InputStream;

public interface CgmToSvgConverter {

    String convert(InputStream cgmStream) throws IOException;
}


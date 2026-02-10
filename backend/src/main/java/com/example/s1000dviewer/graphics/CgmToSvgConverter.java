package com.example.s1000dviewer.graphics;

import java.io.IOException;
import java.io.InputStream;

public interface CgmToSvgConverter {

    String convert(InputStream cgmStream) throws IOException;
}

package com.gf169.gfwalk;

//import android.app.Application;
//import android.test.ApplicationTestCase;
import android.util.Log;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * <a href="http://d.android.com/tools/testing/testing_android.html">Testing Fundamentals</a>
 */
/*
public class ApplicationTest extends ApplicationTestCase<Application> {
    public ApplicationTest() {
        super(Application.class);
    }
}
*/

public class ApplicationTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void getNthPiece() {
        String s = "1111/2222/3333";
        assertEquals("22222", Utils.getNthPiece(s, -2, "\\/"));
    }
}

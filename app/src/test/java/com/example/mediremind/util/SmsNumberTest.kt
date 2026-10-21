package com.example.mediremind.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** Guardian numbers must survive formatting quirks or the carrier silently drops them. */
class SmsNumberTest {

    @Test fun `spaces and dashes are removed`() {
        assertEquals("9876543210", SmsHelper.normalizeNumber("98765 43210"))
        assertEquals("9876543210", SmsHelper.normalizeNumber("98765-432-10"))
    }

    @Test fun `trunk zero of a national number is dropped`() {
        assertEquals("9876543210", SmsHelper.normalizeNumber("09876543210"))
    }

    @Test fun `plus and double-zero prefixes stay international`() {
        assertEquals("+919876543210", SmsHelper.normalizeNumber("+91 98765 43210"))
        assertEquals("+919876543210", SmsHelper.normalizeNumber("00919876543210"))
    }

    @Test fun `plain ten digit number passes through unchanged`() {
        assertEquals("9876543210", SmsHelper.normalizeNumber("9876543210"))
    }

    @Test fun `brackets are removed`() {
        assertEquals("9876543210", SmsHelper.normalizeNumber("(98765) 43210"))
    }
}

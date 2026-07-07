package com.yf.exam.modules.qu.controller;

import com.yf.exam.modules.qu.dto.request.QuQueryReqDTO;
import org.junit.jupiter.api.Test;

import javax.servlet.http.HttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuControllerDownloadContractTest {

    @Test
    void excelDownloadEndpointsWriteResponseDirectly() throws Exception {
        assertEquals(void.class, QuController.class
                .getDeclaredMethod("exportFile", HttpServletResponse.class, QuQueryReqDTO.class)
                .getReturnType());
        assertEquals(void.class, QuController.class
                .getDeclaredMethod("importFileTemplate", HttpServletResponse.class)
                .getReturnType());
    }
}

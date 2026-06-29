package com.yf.exam.modules.paper.service;

import com.yf.exam.modules.paper.dto.request.PaperWordExportReqDTO;
import com.yf.exam.modules.paper.dto.request.PaperRandomWordExportReqDTO;

import javax.servlet.http.HttpServletResponse;

public interface PaperWordExportService {

    void exportWord(PaperWordExportReqDTO reqDTO, HttpServletResponse response);

    void exportRandomWord(PaperRandomWordExportReqDTO reqDTO, HttpServletResponse response);
}

package com.yf.exam.modules.qu.support;

/**
 * AI 导入任务进度回调
 */
public interface ImportTaskProgressListener {

    void onBatchProgress(int completedBatches, int totalBatches, String message);
}

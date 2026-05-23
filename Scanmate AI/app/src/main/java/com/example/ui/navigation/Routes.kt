package com.example.ui.navigation

object Routes {
    const val HOME = "home"
    const val CAMERA_SCAN = "camera_scan"
    const val DOC_DETAIL = "document_detail/{docId}"
    const val QR_TOOLS = "qr_tools"
    const val SETTINGS = "settings"
    const val ZIP_TOOLS = "zip_tools"
    const val AI_ASSISTANT = "ai_assistant"
    const val FILE_MANAGER = "file_manager"

    fun docDetail(docId: Long) = "document_detail/$docId"
}

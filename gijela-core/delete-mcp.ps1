$base = "d:\workspace\gijela\gijela-core\gijela-core-chat\src"
# main: client/* (8) + client/transport/* (3)
Remove-Item -Force "$base\main\java\com\gijela\morpheus\chat\adapter\mcp\client\*.java"
Remove-Item -Force "$base\main\java\com\gijela\morpheus\chat\adapter\mcp\client\transport\*.java"
Remove-Item -Force -Recurse "$base\main\java\com\gijela\morpheus\chat\adapter\mcp\client"
# main: skill subfolder — only McpToolBinding/Source/Sync/Provider migrated; nothing else there
Remove-Item -Force "$base\main\java\com\gijela\morpheus\chat\adapter\mcp\skill\McpToolBinding.java"
Remove-Item -Force "$base\main\java\com\gijela\morpheus\chat\adapter\mcp\skill\McpToolBindingSource.java"
Remove-Item -Force "$base\main\java\com\gijela\morpheus\chat\adapter\mcp\skill\McpSkillSync.java"
Remove-Item -Force "$base\main\java\com\gijela\morpheus\chat\adapter\mcp\skill\McpToolSkillProvider.java"
Remove-Item -Force -Recurse "$base\main\java\com\gijela\morpheus\chat\adapter\mcp\skill"
# main: McpSdkProperties (chat-side)
Remove-Item -Force "$base\main\java\com\gijela\morpheus\chat\config\McpSdkProperties.java"
# tests
Remove-Item -Force "$base\test\java\com\gijela\morpheus\chat\adapter\mcp\client\McpJsonRpcClientTest.java"
Remove-Item -Force "$base\test\java\com\gijela\morpheus\chat\adapter\mcp\client\transport\StdioTransportTest.java"
Remove-Item -Force -Recurse "$base\test\java\com\gijela\morpheus\chat\adapter\mcp\client"
Remove-Item -Force "$base\test\java\com\gijela\morpheus\chat\adapter\mcp\skill\McpToolSkillProviderTest.java"
Remove-Item -Force "$base\test\java\com\gijela\morpheus\chat\adapter\mcp\skill\McpSkillSyncTest.java"
Remove-Item -Force -Recurse "$base\test\java\com\gijela\morpheus\chat\adapter\mcp\skill"
Write-Host "DONE"

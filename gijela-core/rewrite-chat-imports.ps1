$files = @(
  "d:\workspace\gijela\gijela-core\gijela-core-chat\src\main\java\com\gijela\morpheus\chat\service\impl\MyBatisMcpServerService.java",
  "d:\workspace\gijela\gijela-core\gijela-core-chat\src\main\java\com\gijela\morpheus\chat\config\ChatModuleConfig.java"
)
foreach ($f in $files) {
  $c = Get-Content -Raw $f
  $c = $c -replace 'com\.gijela\.morpheus\.chat\.adapter\.mcp\.client\.transport\.','com.gijela.morpheus.llm.sdk.mcp.client.transport.'
  $c = $c -replace 'com\.gijela\.morpheus\.chat\.adapter\.mcp\.client\.','com.gijela.morpheus.llm.sdk.mcp.client.'
  $c = $c -replace 'com\.gijela\.morpheus\.chat\.adapter\.mcp\.skill\.','com.gijela.morpheus.llm.sdk.mcp.skill.'
  $c = $c -replace 'com\.gijela\.morpheus\.chat\.config\.McpSdkProperties','com.gijela.morpheus.llm.sdk.mcp.McpSdkProperties'
  Set-Content -Path $f -Value $c -NoNewline -Encoding UTF8
  Write-Host ("rewrote: " + (Split-Path -Leaf $f))
}
Write-Host "DONE"

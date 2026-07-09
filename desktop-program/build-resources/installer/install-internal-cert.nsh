!macro customInstall
  DetailPrint "Instalando certificado interno de Arles SAS para este usuario..."
  IfFileExists "$INSTDIR\resources\certificates\arles-code-signing.cer" 0 +3
  nsExec::ExecToLog '"$SYSDIR\certutil.exe" -user -f -addstore Root "$INSTDIR\resources\certificates\arles-code-signing.cer"'
  nsExec::ExecToLog '"$SYSDIR\certutil.exe" -user -f -addstore TrustedPublisher "$INSTDIR\resources\certificates\arles-code-signing.cer"'
!macroend

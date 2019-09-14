set NGROK_HOME=C:/DATA/Programs/ngrok

start python image-classify-server.py
start %NGROK_HOME%/ngrok.exe http 8000
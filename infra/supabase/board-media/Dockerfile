FROM python:3.13-slim
WORKDIR /app
RUN pip install --no-cache-dir Pillow==12.3.0
COPY server.py /app/server.py
USER 10001:10001
ENV PYTHONUNBUFFERED=1 PYTHONDONTWRITEBYTECODE=1
CMD ["python", "server.py"]

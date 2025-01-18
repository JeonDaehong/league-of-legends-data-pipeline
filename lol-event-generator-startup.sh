#!/bin/bash

# 사용자가 입력한 게임 수 ( 기본 10 )
NUM=${1:-10}

# 사용자가 입력한 Thread Pool 개수 ( 기본 30 )
NUM2=${2:-30}

# 기본 Kafka 서버 주소
KAFKA_SERVER="127.0.0.1:9092"

# jar 파일 경로
JAR_PATH="/home/daehong/jar/lol-champion-event-generator-1.0-SNAPSHOT-all.jar"

# 명령어 실행
java -jar $JAR_PATH $NUM $NUM2 $KAFKA_SERVER

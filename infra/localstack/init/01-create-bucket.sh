#!/usr/bin/env sh
# Creates the dev S3 bucket on LocalStack startup.
set -eu
awslocal s3api create-bucket --bucket photo-app-dev --region us-east-1 || true
echo "[init] bucket photo-app-dev ready"

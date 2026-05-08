/**
 * Object storage abstraction.
 *
 * <p>{@code BlobStore} is the storage interface — services use only this.
 * {@code s3/} contains the AWS S3 implementation (presigned PUT/GET, key
 * strategy). Per ADR 0005, all photo bytes flow through presigned URLs;
 * the API never proxies image data. Per ADR 0001, the only place
 * {@code S3Client} / {@code S3Presigner} is referenced is inside this
 * package — so swapping to MinIO or GCS later is one new impl bean.
 */
package com.example.photoapp.storage;

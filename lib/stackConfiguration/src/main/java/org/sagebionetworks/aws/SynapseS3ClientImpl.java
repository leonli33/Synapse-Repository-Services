package org.sagebionetworks.aws;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.BucketCrossOriginConfiguration;
import com.amazonaws.services.s3.model.BucketWebsiteConfiguration;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult;
import com.amazonaws.services.s3.model.CopyPartRequest;
import com.amazonaws.services.s3.model.CopyPartResult;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsResult;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.Region;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.util.StringUtils;

public class SynapseS3ClientImpl implements SynapseS3Client {
	
	private Map<Region, AmazonS3> regionSpecificClients;
	
	private Map<String, Region> bucketLocation;
	
	public SynapseS3ClientImpl(Map<Region, AmazonS3> regionSpecificClients) {
		this.regionSpecificClients=regionSpecificClients;
		bucketLocation = Collections.synchronizedMap(new HashMap<String,Region>());
	}
	
	public Region getRegionForBucket(String bucketName) {
		Region result = bucketLocation.get(bucketName);
		// TODO periodically purge cache
		if (result!=null) return result;
		AmazonS3 amazonS3 = getDefaultAmazonClient();
		String location = null;
		try {
			location = amazonS3.getBucketLocation(bucketName);
		}  catch (com.amazonaws.services.s3.model.AmazonS3Exception e) {
			throw new IllegalArgumentException("Failed to determine the Amazon region for bucket '"+bucketName+
					"'. Please ensure that the bucket's policy grants 's3:GetBucketLocation' permission to Synapse.", e);
		}
		if (StringUtils.isNullOrEmpty(location)) result = Region.US_Standard;
		result =  Region.fromValue(location);
		bucketLocation.put(bucketName, result);
		return result;
	}
	
	public AmazonS3 getS3ClientForBucket(String bucket) {
		Region region = getRegionForBucket(bucket);
		return regionSpecificClients.get(region);
	}

	@Override
    public AmazonS3 getDefaultAmazonClient() {
		return regionSpecificClients.get(Region.US_Standard);
	}

	@Override
	public ObjectMetadata getObjectMetadata(String bucketName, String key)
			throws SdkClientException, AmazonServiceException {
		return getS3ClientForBucket(bucketName).getObjectMetadata(bucketName, key);
	}

	@Override
	public void deleteObject(String bucketName, String key) throws SdkClientException, AmazonServiceException {
		getS3ClientForBucket(bucketName).deleteObject( bucketName,  key);
	}

	@Override
	public DeleteObjectsResult deleteObjects(DeleteObjectsRequest deleteObjectsRequest)
			throws SdkClientException, AmazonServiceException {
		return getS3ClientForBucket(deleteObjectsRequest.getBucketName()).deleteObjects(deleteObjectsRequest);
	}

	@Override
	public PutObjectResult putObject(String bucketName, String key, InputStream input, ObjectMetadata metadata)
			throws SdkClientException, AmazonServiceException {
		return getS3ClientForBucket(bucketName).putObject( bucketName,  key,  input,  metadata);
	}

	@Override
	public PutObjectResult putObject(String bucketName, String key, File file)
			throws SdkClientException, AmazonServiceException {
		return getS3ClientForBucket(bucketName).putObject( bucketName, key,  file);
	}

	@Override
	public PutObjectResult putObject(PutObjectRequest putObjectRequest)
			throws SdkClientException, AmazonServiceException {
		return getS3ClientForBucket(putObjectRequest.getBucketName()).putObject( putObjectRequest);
	}

	@Override
	public S3Object getObject(String bucketName, String key) throws SdkClientException, AmazonServiceException {
		return getS3ClientForBucket(bucketName).getObject( bucketName,  key);
	}

	@Override
	public S3Object getObject(GetObjectRequest getObjectRequest) throws SdkClientException, AmazonServiceException {
		return getS3ClientForBucket(getObjectRequest.getBucketName()).getObject( getObjectRequest);
	}

	@Override
	public ObjectMetadata getObject(GetObjectRequest getObjectRequest, File destinationFile)
			throws SdkClientException, AmazonServiceException {
		return getS3ClientForBucket(getObjectRequest.getBucketName()).getObject( getObjectRequest,  destinationFile);
	}

	@Override
	public ObjectListing listObjects(String bucketName, String prefix)
			throws SdkClientException, AmazonServiceException {
		return getS3ClientForBucket(bucketName).listObjects( bucketName,  prefix);
	}

	@Override
	public ObjectListing listObjects(ListObjectsRequest listObjectsRequest)
			throws SdkClientException, AmazonServiceException {
		return getS3ClientForBucket(listObjectsRequest.getBucketName()).listObjects( listObjectsRequest);
	}

	@Override
	public Bucket createBucket(String bucketName) throws SdkClientException, AmazonServiceException {
		return getDefaultAmazonClient().createBucket( bucketName);
	}

	@Override
	public boolean doesObjectExist(String bucketName, String objectName)
			throws AmazonServiceException, SdkClientException {
		return getS3ClientForBucket(bucketName).doesObjectExist( bucketName,  objectName);
	}

	@Override
	public void setBucketCrossOriginConfiguration(String bucketName,
			BucketCrossOriginConfiguration bucketCrossOriginConfiguration) {
		getS3ClientForBucket(bucketName).setBucketCrossOriginConfiguration( bucketName,
				 bucketCrossOriginConfiguration);
	}

	@Override
	public URL generatePresignedUrl(GeneratePresignedUrlRequest generatePresignedUrlRequest) throws SdkClientException {
		return getS3ClientForBucket(generatePresignedUrlRequest.getBucketName()).generatePresignedUrl( generatePresignedUrlRequest);
	}

	@Override
	public InitiateMultipartUploadResult initiateMultipartUpload(InitiateMultipartUploadRequest request)
			throws SdkClientException, AmazonServiceException {
		return getS3ClientForBucket(request.getBucketName()).initiateMultipartUpload( request);
	}

	@Override
	public CopyPartResult copyPart(CopyPartRequest copyPartRequest) throws SdkClientException, AmazonServiceException {
		return getS3ClientForBucket(copyPartRequest.getDestinationBucketName()).copyPart( copyPartRequest);
	}

	@Override
	public CompleteMultipartUploadResult completeMultipartUpload(CompleteMultipartUploadRequest request)
			throws SdkClientException, AmazonServiceException {
		return getS3ClientForBucket(request.getBucketName()).completeMultipartUpload( request);
	}

	@Override
	public void setBucketWebsiteConfiguration(String bucketName, BucketWebsiteConfiguration configuration)
			throws SdkClientException, AmazonServiceException {
		getS3ClientForBucket(bucketName).setBucketWebsiteConfiguration( bucketName,  configuration);
	}

	@Override
	public void setBucketPolicy(String bucketName, String policyText)
			throws SdkClientException, AmazonServiceException {
		getS3ClientForBucket(bucketName).setBucketPolicy( bucketName,  policyText);
	}

	@Override
	public BucketCrossOriginConfiguration getBucketCrossOriginConfiguration(String bucketName) {
		return getS3ClientForBucket(bucketName).getBucketCrossOriginConfiguration(bucketName);
	}
}

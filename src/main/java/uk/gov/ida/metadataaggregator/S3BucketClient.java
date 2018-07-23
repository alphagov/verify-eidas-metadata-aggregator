package uk.gov.ida.metadataaggregator;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.util.StringInputStream;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import uk.gov.ida.metadataaggregator.metadatastore.MetadataStore;
import uk.gov.ida.metadataaggregator.metadatastore.MetadataStoreException;
import uk.gov.ida.saml.serializers.XmlObjectToElementTransformer;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

class S3BucketClient implements MetadataStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3BucketClient.class);

    private final String bucketName;
    private final AmazonS3Client s3Client;

    public S3BucketClient(String configBucket, AmazonS3Client s3Client) {
        this.bucketName = configBucket;
        this.s3Client = s3Client;
    }

    @Override
    public void uploadMetadata(String resource, EntityDescriptor metadataNode) throws MetadataStoreException {
        String metadataString = serialise(metadataNode);
        ObjectMetadata objectMetadata = objectMetadata(metadataString.length());

        StringInputStream metadataStream;
        try {
            metadataStream = new StringInputStream(metadataString);
        } catch (UnsupportedEncodingException e) {
            throw new MetadataStoreException("Error opening metadata file stream to store", e);
        }

        try {
            s3Client.putObject(new PutObjectRequest(bucketName, resource, metadataStream, objectMetadata));
        } catch (RuntimeException e) {
            throw new MetadataStoreException("Error uploading metadata to S3 bucket", e);
        }
    }

    @Override
    public void deleteMetadata(String resource) throws MetadataStoreException {
        try {
            LOGGER.info("Deleting metadata with key: {} from S3 bucket: {}", resource, bucketName);
            s3Client.deleteObject(new DeleteObjectRequest(bucketName, resource));
        } catch (RuntimeException e) {
            throw new MetadataStoreException("Error removing metadata from S3 bucket", e);
        }
    }

    @Override
    public List<String> getAllHexEncodedUrlsFromS3Bucket() throws MetadataStoreException {
        List<String> bucketKeyList = new ArrayList<>();
        List<S3ObjectSummary> bucketObjects;

        try {
            bucketObjects = s3Client.listObjects(bucketName).getObjectSummaries();
        } catch (RuntimeException e) {
            throw new MetadataStoreException("Error retrieving objects from S3 Bucket", e);
        }

        for (S3ObjectSummary s3ObjectSummary : bucketObjects) {
            bucketKeyList.add(s3ObjectSummary.getKey());
        }

        return bucketKeyList;
    }

    private ObjectMetadata objectMetadata(int contentLength) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(contentLength);
        return metadata;
    }

    private String serialise(EntityDescriptor node) {
        XmlObjectToElementTransformer<EntityDescriptor> transformer = new XmlObjectToElementTransformer<>();
        Element element = transformer.apply(node);
        DOMImplementationLS domImplLS = (DOMImplementationLS) element.getOwnerDocument()
                .getImplementation();
        LSSerializer serializer = domImplLS.createLSSerializer();
        return serializer.writeToString(element);
    }
}

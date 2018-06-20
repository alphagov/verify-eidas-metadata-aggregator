package uk.gov.ida.metadataaggregator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.ida.metadataaggregator.config.AggregatorConfig;
import uk.gov.ida.metadataaggregator.config.ConfigSource;
import uk.gov.ida.metadataaggregator.config.ConfigSourceException;
import uk.gov.ida.metadataaggregator.metadatasource.CountryMetadataSource;
import uk.gov.ida.metadataaggregator.metadatasource.MetadataSourceException;
import uk.gov.ida.metadataaggregator.metadatastore.MetadataStore;
import uk.gov.ida.metadataaggregator.metadatastore.MetadataStoreException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MetadataAggregator {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataAggregator.class);

    private final ConfigSource configSource;
    private final CountryMetadataSource countryMetadataCurler;
    private final MetadataStore metadataStore;

    public MetadataAggregator(ConfigSource configSource,
                              CountryMetadataSource countryMetadataCurler,
                              MetadataStore metadataStore) {
        this.configSource = configSource;
        this.metadataStore = metadataStore;
        this.countryMetadataCurler = countryMetadataCurler;
    }

    public void aggregateMetadata() {
        AggregatorConfig config;
        try {
            config = configSource.downloadConfig();
        } catch (ConfigSourceException e) {
            LOGGER.error("Metadata Aggregator error - Unable to download Aggregator Config file: {}", e.getMessage());
            return;
        }

        LOGGER.info("Processing country metadatasource");

        int successfulUploads = 0;
        Collection<String> configMetadataUrls = config.getMetadataUrls();

        deleteMetadataWhichIsNotInConfig(configMetadataUrls);

        for (String url : configMetadataUrls) {
            boolean successfulUpload = processMetadataFrom(url);
            if (successfulUpload) successfulUploads++;
        }

        LOGGER.info("Finished processing country metadatasource with {} successful uploads out of {}", successfulUploads, configMetadataUrls.size());
    }

    private boolean processMetadataFrom(String metadataUrl) {
        String countryMetadataFile;
        try {
            countryMetadataFile = countryMetadataCurler.downloadMetadata(metadataUrl);
        } catch (MetadataSourceException e) {
            LOGGER.error("Error downloading metadatasource file {} Exception: {}", metadataUrl, e.getMessage());
            deleteMetadataWithMetadataUrl(metadataUrl);
            return false;
        }

        try {
            metadataStore.uploadMetadata(metadataUrl, countryMetadataFile);
        } catch (MetadataStoreException e) {
            LOGGER.error("Error uploading metadatasource file {} Exception: {}", metadataUrl, e.getMessage());
            deleteMetadataWithMetadataUrl(metadataUrl);
            return false;
        }
        return true;
    }

    private void deleteMetadataWhichIsNotInConfig(Collection<String> configMetadataUrls) {
        List<String> hexedConfigUrls = new ArrayList<>();

        for (String configMetadataUrl : configMetadataUrls) {
            hexedConfigUrls.add(HexUtils.encodeString(configMetadataUrl));
        }

        List<String> toRemoveHexedBucketUrls = getAllHexEncodedUrlsFromS3Bucket();

        toRemoveHexedBucketUrls.removeAll(hexedConfigUrls);

        for (String hexedBucketUrl : toRemoveHexedBucketUrls) {
            deleteMetadataWithHexEncodedUrl(hexedBucketUrl);
        }
    }

    private List<String> getAllHexEncodedUrlsFromS3Bucket() {
        List<String> hexEncodedUrls = new ArrayList<>();
        try {
            hexEncodedUrls = metadataStore.getAllHexEncodedUrlsFromS3Bucket();
        } catch (MetadataStoreException e) {
            LOGGER.error("Metadata Aggregator error - Unable to retrieve keys from S3 bucket", e.getMessage());
        }
        return hexEncodedUrls;
    }

    private void deleteMetadataWithMetadataUrl(String metadataUrl) {
        try {
            metadataStore.deleteMetadataWithMetadataUrl(metadataUrl);
        } catch (MetadataStoreException e) {
            LOGGER.error("Error deleting metadatasource file with metadataUrl: {} Exception: {}", metadataUrl, e.getMessage());
        }
    }

    private void deleteMetadataWithHexEncodedUrl(String hexEncodedUrl) {
        try {
            metadataStore.deleteMetadataWithHexEncodedUrl(hexEncodedUrl);
        } catch (MetadataStoreException e) {
            LOGGER.error("Error deleting metadatasource file with hexEncodedUrl: {} Exception: {}", hexEncodedUrl, e.getMessage());
        }
    }
}

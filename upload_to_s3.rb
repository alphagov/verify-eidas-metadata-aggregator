#!/usr/bin/env ruby

require 'aws-sdk-s3'
require 'yaml'
require 'cgi'

aws_region = 'eu-west-1'
bucket_name = 'govukverify-eidas-metadata-aggregator-dev'
encryption_algorithm = 'AES256'
metadata_list_file = File.join(__dir__, 'metadata_list.yml')

metadata_list = YAML.load_file metadata_list_file
abort("Error: File supplied is not valid YAML") unless metadata_list

s3 = Aws::S3::Resource.new(region: aws_region)

metadata_list.each do |country, url|
  obj = s3.bucket(bucket_name).object(CGI.escape url)
  metadata = `curl -A "Mozilla" --max-time 15 #{url}`
  obj.put(body: metadata, server_side_encryption: encryption_algorithm, acl: 'public-read', content_type: 'application/samlmetadata+xml')
end


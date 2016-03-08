/*
* Copyright (C) 2014-2016 LinkedIn Corp. All rights reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use
* this file except in compliance with the License. You may obtain a copy of the
* License at  http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software distributed
* under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
* CONDITIONS OF ANY KIND, either express or implied.
*/

package gobblin.data.management.version.finder;

import gobblin.data.management.version.FileSystemDatasetVersion;
import gobblin.data.management.version.StringDatasetVersion;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.typesafe.config.Config;


/**
 * Finds watermarked dataset versions as direct subdirectories of the dataset directory. The watermark is assumed
 * to be part of the subdirectory name. By default, the watermark is the subdirectory name itself, but a regular
 * expression can be provided to extract the watermark from the name. The watermarks will be sorted by String
 * sorting.
 *
 * <p>
 *   For example, snapshots of a database can be named by the unix timestamp when the snapshot was dumped:
 *   /path/to/snapshots/1436223009-snapshot
 *   /path/to/snapshots/1436234210-snapshot
 *   In this case the versions are 1436223009-snapshot, 1436234210-snapshot. Since the watermark is at the
 *   beginning of the name, the natural string ordering is good enough to sort the snapshots, so no regexp is
 *   required to extract the actual watermark.
 * </p>
 */
public class WatermarkDatasetVersionFinder extends DatasetVersionFinder<StringDatasetVersion> {

  public static final Logger LOGGER = LoggerFactory.getLogger(WatermarkDatasetVersionFinder.class);

  public static final String WATERMARK_REGEX_KEY = "gobblin.dataset.version.watermark.regex";

  /**
   * @deprecated use {@link #WATERMARK_REGEX_KEY} instead.
   */
  @Deprecated
  public static final String RETENTION_WATERMARK_REGEX_KEY = "gobblin.retention.watermark.regex";

  private Optional<Pattern> pattern;

  public WatermarkDatasetVersionFinder(FileSystem fs, Properties props) {
    super(fs, props);
    if (props.containsKey(WATERMARK_REGEX_KEY)) {
      initPattern(props.getProperty(WATERMARK_REGEX_KEY));
    } else if (props.containsKey(RETENTION_WATERMARK_REGEX_KEY)) {
      initPattern(props.getProperty(RETENTION_WATERMARK_REGEX_KEY));
    } else {
      this.pattern = Optional.absent();
    }
  }

  public WatermarkDatasetVersionFinder(FileSystem fs, Config config) {
    super(fs);
    if (config.hasPath(WATERMARK_REGEX_KEY)) {
      initPattern(config.getString(WATERMARK_REGEX_KEY));
    } else if (config.hasPath(RETENTION_WATERMARK_REGEX_KEY)) {
      initPattern(config.getString(RETENTION_WATERMARK_REGEX_KEY));
    } else {
      this.pattern = Optional.absent();
    }
  }

  private void initPattern(String patternString) {
    this.pattern = Optional.of(patternString).transform(new Function<String, Pattern>() {
      @Nullable
      @Override
      public Pattern apply(String input) {
        return Pattern.compile(input);
      }
    });
  }

  @Override
  public Class<? extends FileSystemDatasetVersion> versionClass() {

    return StringDatasetVersion.class;
  }

  @Override
  public Path globVersionPattern() {
    return new Path("*");
  }

  @Override
  public StringDatasetVersion getDatasetVersion(Path pathRelativeToDatasetRoot, Path fullPath) {
    if (this.pattern.isPresent()) {
      Matcher matcher = this.pattern.get().matcher(pathRelativeToDatasetRoot.getName());
      if (!matcher.find() || matcher.groupCount() < 1) {
        LOGGER.warn("Candidate dataset version at " + pathRelativeToDatasetRoot
            + " does not match expected pattern. Ignoring.");
        return null;
      }
      return new StringDatasetVersion(matcher.group(1), fullPath);
    } else {
      return new StringDatasetVersion(pathRelativeToDatasetRoot.getName(), fullPath);
    }
  }

}

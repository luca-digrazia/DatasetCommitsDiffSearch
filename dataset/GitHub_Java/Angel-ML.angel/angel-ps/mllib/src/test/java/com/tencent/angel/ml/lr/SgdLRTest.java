/*
 * Tencent is pleased to support the open source community by making Angel available.
 *
 * Copyright (C) 2017 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package com.tencent.angel.ml.lr;

import com.tencent.angel.conf.AngelConfiguration;
import com.tencent.angel.ml.classification.lr.LRRunner;
import com.tencent.angel.ml.conf.MLConf;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.mapreduce.lib.input.CombineTextInputFormat;
import org.apache.log4j.PropertyConfigurator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Gradient descent LR UT.
 */
public class SgdLRTest {
  private Configuration conf = new Configuration();
  private static final Log LOG = LogFactory.getLog(SgdLRTest.class);
  private static String LOCAL_FS = FileSystem.DEFAULT_FS;
  private static String TMP_PATH = System.getProperty("java.io.tmpdir", "/tmp");
  static {
    PropertyConfigurator.configure("../conf/log4j.properties");
  }
  /**
   * set parameter values of conf
   */
  @Before
  public void setConf() {

    // Feature number of train data
    int featureNum = 124;
    // Total iteration number
    int epochNum = 20;
    // Validation sample Ratio
    double vRatio = 0.1;
    // Data format, libsvm or dummy
    String dataFmt = "libsvm";
    // Train batch number per epoch.
    double spRatio = 1.0;
    // Batch number
    int batchNum = 10;

    // Learning rate
    double learnRate = 1.0;
    // Decay of learning rate
    double decay = 0.1;
    // Regularization coefficient
    double reg = 0.2;

    // Set local deploy mode
    conf.set(AngelConfiguration.ANGEL_DEPLOY_MODE, "LOCAL");

    // Set basic configuration keys
    conf.setBoolean("mapred.mapper.new-api", true);
    conf.set(AngelConfiguration.ANGEL_INPUTFORMAT_CLASS, CombineTextInputFormat.class.getName());
    conf.setBoolean(AngelConfiguration.ANGEL_JOB_OUTPUT_PATH_DELETEONEXIST, true);

    // Set data format
    conf.set(MLConf.ML_DATAFORMAT(), dataFmt);

    //set angel resource parameters #worker, #task, #PS
    conf.setInt(AngelConfiguration.ANGEL_WORKERGROUP_NUMBER, 1);
    conf.setInt(AngelConfiguration.ANGEL_WORKER_TASK_NUMBER, 1);
    conf.setInt(AngelConfiguration.ANGEL_PS_NUMBER, 1);

    //set sgd LR algorithm parameters #feature #epoch
    conf.set(MLConf.ML_FEATURE_NUM(), String.valueOf(featureNum));
    conf.set(MLConf.ML_EPOCH_NUM(), String.valueOf(epochNum));
    conf.set(MLConf.ML_BATCH_SAMPLE_Ratio(), String.valueOf(spRatio));
    conf.set(MLConf.ML_VALIDATE_RATIO(), String.valueOf(vRatio));
    conf.set(MLConf.ML_LEARN_RATE(), String.valueOf(learnRate));
    conf.set(MLConf.ML_LEARN_DECAY(), String.valueOf(decay));
    conf.set(MLConf.ML_REG_L2(), String.valueOf(reg));
  }

  @Test
  public void trainOnLocalClusterTest() throws Exception {
    String inputPath = "./src/test/data/lr/a9a.train";
    String savePath = LOCAL_FS + TMP_PATH + "/model";
    String logPath = LOCAL_FS + TMP_PATH + "/LRlog";

    // Set trainning data path
    conf.set(AngelConfiguration.ANGEL_TRAIN_DATA_PATH, inputPath);
    // Set save model path
    conf.set(AngelConfiguration.ANGEL_SAVE_MODEL_PATH, savePath);
    // Set log path
    conf.set(AngelConfiguration.ANGEL_LOG_PATH, logPath);
    // Set actionType train
    conf.set(AngelConfiguration.ANGEL_ACTION_TYPE, MLConf.ANGEL_ML_TRAIN());


    LRRunner runner = new LRRunner();
    runner.train(conf);
 }


  @Test
  public void incTrainTest() {
    try{
      String inputPath = "./src/test/data/lr/a9a.train";
      String loadPath = LOCAL_FS + TMP_PATH + "/model";
      String savePath = LOCAL_FS + TMP_PATH + "/newmodel";
      String logPath = LOCAL_FS + TMP_PATH + "/LRlog";

      // Set trainning data path
      conf.set(AngelConfiguration.ANGEL_TRAIN_DATA_PATH, inputPath);
      // Set load model path
      conf.set(AngelConfiguration.ANGEL_LOAD_MODEL_PATH, loadPath);
      // Set save model path
      conf.set(AngelConfiguration.ANGEL_SAVE_MODEL_PATH, savePath);
      // Set log path
      conf.set(AngelConfiguration.ANGEL_LOG_PATH, logPath);
      // Set actionType incremental train
      conf.set(AngelConfiguration.ANGEL_ACTION_TYPE, MLConf.ANGEL_ML_INC_TRAIN());

      LRRunner runner = new LRRunner();
      runner.incTrain(conf);
    } catch (Throwable x) {
      LOG.error("run incTrainTest failed ", x);
    }
  }

  @Test
  public void predictTest() {
    try {
      String inputPath = "./src/test/data/lr/a9a.test";
      String loadPath = LOCAL_FS + TMP_PATH + "/model";
      String predictPath = LOCAL_FS + TMP_PATH + "/predict";

      // Set trainning data path
      conf.set(AngelConfiguration.ANGEL_TRAIN_DATA_PATH, inputPath);
      // Set load model path
      conf.set(AngelConfiguration.ANGEL_LOAD_MODEL_PATH, loadPath);
      // Set predict result path
      conf.set(AngelConfiguration.ANGEL_PREDICT_PATH, predictPath);
      // Set actionType prediction
      conf.set(AngelConfiguration.ANGEL_ACTION_TYPE, MLConf.ANGEL_ML_INC_TRAIN());

      conf.set(AngelConfiguration.ANGEL_ACTION_TYPE, MLConf.ANGEL_ML_PREDICT());
      LRRunner runner = new LRRunner();

      runner.predict(conf);
    } catch (Throwable x) {
      LOG.error("run predictTest failed ", x);
    }
  }
}

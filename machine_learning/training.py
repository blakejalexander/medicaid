#!/usr/bin/env python3
""" Medicaid neural network classification model training file.
    Modified from the TensorFlow Iris flower classification example.
    See: https://www.tensorflow.org/get_started/tflearn
"""

import os
import sys

import numpy as np

# Import tensorflow, reduce log level so we don't see warnings about the
# distribution of TensorFlow not supporting instructions available on this
# machine etc. etc.
os.environ['TF_CPP_MIN_LOG_LEVEL']='2'
import tensorflow as tf

# Data sets
MEDICAID_TRAINING = "training.csv"
MEDICAID_TEST = "test.csv"

# Model directory
MEDICAID_MODEL_DIR = os.getcwd() + "/medicaid_model"

# Number of classes to classify as
NUMBER_CLASSES = 4

def class_to_label(label):
    if label == 0:
        return "Fall"
    elif label == 1:
        return "Jump"
    elif label == 2:
        return "Walking"
    elif label == 3:
        return "Bump"
    else:
        return "NAME_ME"

def main():

    # If the training and test sets aren't stored locally, abort mission
    if not os.path.exists(MEDICAID_TRAINING):
        print("error: no training data")
        sys.exit(1)

    if not os.path.exists(MEDICAID_TEST):
        print("error: no test data")
        sys.exit(1)

    # Load datasets.
    training_set = tf.contrib.learn.datasets.base.load_csv_with_header(
            filename=MEDICAID_TRAINING,
            target_dtype=np.int,
            features_dtype=np.float32)
    test_set = tf.contrib.learn.datasets.base.load_csv_with_header(
            filename=MEDICAID_TEST,
            target_dtype=np.int,
            features_dtype=np.float32)

    # Specify that all features have real-value data
    feature_columns = [tf.contrib.layers.real_valued_column("", dimension=4)]

    # Build 3 layer DNN (classifier) with 10, 20, 10 hidden units respectively.
    classifier = tf.contrib.learn.DNNClassifier(feature_columns=feature_columns,
                                                hidden_units=[ 10, 30, 10],
                                                n_classes=NUMBER_CLASSES,
                                                model_dir=MEDICAID_MODEL_DIR)

    # Define the training inputs
    def get_train_inputs():
        x = tf.constant(training_set.data)
        y = tf.constant(training_set.target)

        return x, y

    # Fit model.
    classifier.fit(input_fn=get_train_inputs, steps=100000)

    # Define the test inputs
    def get_test_inputs():
        x = tf.constant(test_set.data)
        y = tf.constant(test_set.target)

        return x, y

    # Evaluate the accuracy of the model after training accuracy.
    accuracy_score = classifier.evaluate(input_fn=get_test_inputs,
        steps=1)["accuracy"]

    print(("\nTest Accuracy: {0:f}\n".format(accuracy_score)))

    # Classify new samples, one of each event type, a jump and a fall
    # TODO: Blake - these samples are just rounded values of some random rows
    # in the test set.
    def new_samples():
        return np.array(
            [[ 505,	0.82,	16.38,	9.91 ],
             [ 346,	0.95,	19.37,	9.89 ]], dtype=np.float32)

    predictions = list(classifier.predict(input_fn=new_samples))
    predictions = [ class_to_label(i) for i in predictions ]

    print(("New Samples, Class Predictions:    {}\n".format(predictions)))

if __name__ == "__main__":
        main()

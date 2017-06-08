#!/usr/bin/env python3
""" TCP Server for answering and classifying features of fall-like events. """

import socket
import os
import sys
import time
import threading
import json

os.environ['TF_CPP_MIN_LOG_LEVEL']='2'

import numpy as np
import tensorflow as tf

tf.logging.set_verbosity(tf.logging.ERROR)


# Data sets
MEDICAID_TRAINING = "training.csv"
MEDICAID_TEST = "test.csv"

# Model directory
MEDICAID_MODEL_DIR = os.getcwd() + "/medicaid_model"
MEDICAID_MODEL_DIR_TRAINED = MEDICAID_MODEL_DIR + "/trained"

# Number of classes to classify to
NUMBER_CLASSES = 4

# Number of features to classify from
NUMBER_FEATURES = 4


SERVER_BIND_IP = "0.0.0.0"
SERVER_PORT = 4011

server_done = False

""" Loads the stored TensorFlow model. Note this is an incredibly ugly hack.
    contrib.learn.DNNClassifier no longer has explicit save/restore functions
    like the older predecessors. Theres also a bug (according to github) where
    the model can't be restored from a stored checkpoint without at least one
    step of training.

    tl;dr ugly hacks to get model from training.py imported, make sure that
    training.py is ran in this directory first or there is atleast a saved
    checkpoint data for the model in this direction at MEDICAID_MODEL_DIR
"""
def load_tensorflow_model():

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
    feature_columns = [tf.contrib.layers.real_valued_column("",
        dimension=NUMBER_FEATURES)]

    # Build 3 layer DNN (classifier) with 10, 20, 10 hidden units respectively.
    classifier = tf.contrib.learn.DNNClassifier(feature_columns=feature_columns,
        hidden_units=[ 10, 20, 10],
        n_classes=NUMBER_CLASSES,
        model_dir=MEDICAID_MODEL_DIR,
        config=tf.contrib.learn.RunConfig(save_checkpoints_secs=1))

    # Define the training inputs
    def get_train_inputs():
        x = tf.constant(training_set.data)
        y = tf.constant(training_set.target)

        return x, y

    # Ugly hack. Required because TensorFlow contrib.learn is very buggy
    # at time of writing. see: https://github.com/tensorflow/tensorflow/issues/3340
    classifier.fit(input_fn=get_train_inputs, steps=0)

    def get_test_inputs():
        x = tf.constant(test_set.data)
        y = tf.constant(test_set.target)
        return x, y

    # Evaluate the accuracy of the model, from restored checkpoint data
    accuracy_score = classifier.evaluate(input_fn=get_test_inputs,
        steps=1)["accuracy"]
    print(("\nModel Accuracy: {0:f}\n".format(accuracy_score)))

    # Return the classifier object
    return classifier

""" Returns a class ID for the class identified by features, which is an
    (ordered) list of the features for our model. """
def classify(classifier, features):
    def input_func(feat=features):
        return np.array(
            [[ f for f in feat ]], dtype=np.float32)

    prediction = list(classifier.predict_classes(input_fn=input_func))
    return int(prediction[0])

def _server_thread():

    server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_sock.bind((SERVER_BIND_IP, SERVER_PORT))

    server_sock.listen()
    print("CSSE4011: Fall-like event classification"
        " server started!")
    print("Listening on {}:{}".format(SERVER_BIND_IP, SERVER_PORT))

    # Load the TensorFlow classification model
    print("Loading TensorFlow DNNClassifier model from saved checkpoint...")
    classifier = load_tensorflow_model()

    while True:

        client_sock, address = server_sock.accept()
        print("Connection from {}:{} accepted!".format(
            address[0], address[1]))

        # Block and recieve a request from a client.
        req = client_sock.recv(4096)

        # Verify packet contents is a JSON of the correct format
        try:
            j = json.loads(req.decode())
        except json.decoder.JSONDecodeError:
            print("JSONDecodeError, rejecting connection")
            client_sock.close()
            continue

        print("Received {}".format(str(j)))

        # Bail if json is the wrong format
        if 'impact_duration' not in j:
            print("error: no impact_duration")
            continue
        if 'impact_violence' not in j:
            print("error: no impact_violence")
            continue
        if 'impact_average' not in j:
            print("error: no impact_average")
            continue
        if 'post_impact_average' not in j:
            print("error: no post_impact_average")
            continue

        # Classify the features
        classification = classify(classifier,
            [j['impact_duration'], j['impact_violence'],
            j['impact_average'], j['post_impact_average']])

        # Send the result to the client
        client_sock.send(b'%d\n' % classification)

        # Close the connection
        client_sock.close()

    print("Server shutting down... press CTRL-C to exit")
    server_done = True

def main():

    server_thread = threading.Thread(target=_server_thread)
    server_thread.start()

    try:
        while not server_done:
            time.sleep(1)
    except KeyboardInterrupt:
        print("KeyBoardInterrupt...")
        sys.exit(0)

if __name__ == "__main__":
    main()
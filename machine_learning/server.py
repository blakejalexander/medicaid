#!/usr/bin/env python3
""" TCP Server for answering and classifying features of fall-like events. """

import socket
import sys
import time
import threading
import json

SERVER_BIND_IP = "0.0.0.0"
SERVER_PORT = 4011

server_done = False

def _server_thread():

    server_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server_sock.bind((SERVER_BIND_IP, SERVER_PORT))

    server_sock.listen()
    print("CSSE4011: Fall-like event classification"
        " server started!")
    print("Listening on {}:{}".format(SERVER_BIND_IP, SERVER_PORT))

    # Load the TensorFlow classification model

    print("TensorFlow DNNClassifier loaded!")

    while not server_done:

        client_sock, address = server_sock.accept()
        print("Connection from {}:{} accepted!".format(
            address[0], address[1]))

        # Block and recieve a request from a client.
        req = client_sock.recv(4096)
        print("Received {}".format(req))

        # Verify packet contents is a JSON of the correct format
        try:
            j = json.loads(req.decode())
        except json.decoder.JSONDecodeError:
            print("JSONDecodeError, rejecting connection")
            client_sock.close()
            continue

        print(j)

        if 'impact_duration' not in j:
            print("error: no impact_duration")
        if 'impact_violence' not in j:
            print("error: no impact_violence")
        if 'impact_average' not in j:
            print("error: no impact_average")
        if 'post_impact_average' not in j:
            print("error: no post_impact_average")



        # Classify the features


        # Send the result


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
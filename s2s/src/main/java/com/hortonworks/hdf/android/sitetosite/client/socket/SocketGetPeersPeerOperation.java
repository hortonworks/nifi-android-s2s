/*
 * Copyright 2017 Hortonworks, Inc.
 * All rights reserved.
 *
 *   Hortonworks, Inc. licenses this file to you under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License. You may obtain a copy of the License at
 *   http://www.apache.org/licenses/LICENSE-2.0
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 * See the associated NOTICE file for additional information regarding copyright ownership.
 */

package com.hortonworks.hdf.android.sitetosite.client.socket;

import com.hortonworks.hdf.android.sitetosite.client.peer.Peer;
import com.hortonworks.hdf.android.sitetosite.client.peer.PeerOperation;
import com.hortonworks.hdf.android.sitetosite.client.protocol.RequestType;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class SocketGetPeersPeerOperation implements PeerOperation<List<Peer>, SocketPeerConnector> {

    private static final Logger logger = Logger.getLogger(SocketGetPeersPeerOperation.class.getName());

    @Override
    public List<Peer> perform(Peer peer, SocketPeerConnector connectionManager) throws IOException {
        Socket socket = connectionManager.openConnection(false).getSocket();
        OutputStream outputStream = socket.getOutputStream();
        InputStream inputStream = socket.getInputStream();
        DataOutputStream dos = new DataOutputStream(outputStream);
        try {
            RequestType.REQUEST_PEER_LIST.writeRequestType(dos);
            dos.flush();
            DataInputStream dis = new DataInputStream(inputStream);
            int numPeers = dis.readInt();
            List<Peer> result = new ArrayList<>();
            for (int i = 0; i < numPeers; i++) {
                final String hostname = dis.readUTF();
                final int port = dis.readInt();
                final boolean secure = dis.readBoolean();
                final int flowFileCount = dis.readInt();
                result.add(new Peer(hostname, 0, port, secure, flowFileCount));
            }
            return result;
        } finally {
            try {
                RequestType.SHUTDOWN.writeRequestType(dos);
                dos.flush();
            } catch (IOException e) {
                logger.warning("Data output stream could not be flushed cleanly when done using socket.");
            }

            try {
                outputStream.close();
            } catch (IOException e) {
                logger.warning("Output stream could not be closed cleanly when done using socket.");
            }
            try {
                inputStream.close();
            } catch (IOException e) {
                logger.warning("Input stream could not be closed cleanly when done using socket.");
            }
            try {
                socket.close();
            } catch (IOException e) {
                logger.warning("Socket could not be closed cleanly when done using socket.");
            }
        }
    }
}

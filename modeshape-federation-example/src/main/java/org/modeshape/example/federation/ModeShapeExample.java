/*
 * ModeShape (http://www.modeshape.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modeshape.example.federation;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Repository;
import javax.jcr.Session;

import org.modeshape.common.collection.Problems;
import org.modeshape.common.util.FileUtil;
import org.modeshape.common.util.IoUtil;
import org.modeshape.jcr.ModeShapeEngine;
import org.modeshape.jcr.RepositoryConfiguration;
import org.picketbox.factories.SecurityFactory;

public class ModeShapeExample {

    public static void main( String[] argv ) {

        // Create and start the engine ...
        ModeShapeEngine engine = new ModeShapeEngine();
        engine.start();

        // Load the configuration for a repository via the classloader (can also use path to a file)...
        Repository repository = null;
        String repositoryName = null;
        try {
            URL url = ModeShapeExample.class.getClassLoader().getResource("repository-config.json");
            RepositoryConfiguration config = RepositoryConfiguration.read(url);

            // Verify the configuration for the repository ...
            Problems problems = config.validate();
            if (problems.hasErrors()) {
                System.err.println("Problems starting the engine.");
                System.err.println(problems);
                System.exit(-1);
            }

            // Deploy the repository ...
            repository = engine.deploy(config);
            repositoryName = config.getName();
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(-1);
            return;
        }

        Session session = null;
        try {
			// setup some global Picketbox state
			SecurityFactory.prepare();

            // Get the repository
            repository = engine.getRepository(repositoryName);

            // Create a session ...
            session = repository.login("default");

            // Get the root node ...
            Node root = session.getRootNode();
            assert root != null;
            String workspaceName = session.getWorkspace().getName();
            System.out.println("Found the root node in the \"" + workspaceName + "\" workspace");

			Node rootFolder = session.getNode("/cmis");
			System.out.println("/cmis (projection root)");

            NodeIterator childIterator = rootFolder.getNodes();
            while (childIterator.hasNext()) {
                Node child = childIterator.nextNode();
                String primaryType = child.getPrimaryNodeType().getName();
                System.out.println("+---> " + child.getName() + " (" + primaryType + ")");
                child.addMixin("file:anyProperties");
                child.setProperty("customProperty", "custom value");
            }

            session.save();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (session != null) {
                session.logout();
            }
            System.out.println("Shutting down engine ...");
            try {
                engine.shutdown().get();
                System.out.println("Success!");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static File prepareFS() throws Exception {
        //remove the folder which stores repository data
        FileUtil.delete("target/federation_repository");

        //remove the folder where external content is located (this will be recreated by the repository)
        File folder = new File("target/files");
        if (folder.exists()) {
            FileUtil.delete("target/files");
        }
        folder.mkdir();
        if (!folder.exists() || !folder.canRead() || !folder.isDirectory()) {
            throw new IllegalStateException("The " + folder.getAbsolutePath() + " folder cannot be accessed");
        }

        //create some files in the folder which will be read by the repository
        createFile(folder, "file1");
        createFile(folder, "file2");
        createFile(folder, "file3");
        new File(folder, "folder1").mkdir();

        return folder;
    }

    private static void createFile( File parent,
                                    String path ) throws Exception {
        File file = new File(parent, path);
        IoUtil.write(new ByteArrayInputStream("some content".getBytes()), new FileOutputStream(file));
    }
}

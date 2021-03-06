/**
 * Copyright 2009 DigitalPebble Ltd
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.digitalpebble.classification;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.digitalpebble.classification.util.UnZip;

public abstract class TextClassifier
{
    protected Lexicon lexicon;
    private long lastmodifiedLexicon = 0l;
    protected String pathResourceDirectory;

    public static TextClassifier getClassifier(String pathResourceDirectory)
            throws Exception
    {
        File resourceDirectoryFile = new File(pathResourceDirectory);
        return getClassifier(resourceDirectoryFile);
    }

    /**
     * Returns a specific instance of a Text Classifier given a resource
     * Directory
     *
     * @throws Exception
     */
    public static TextClassifier getClassifier(File resourceDirectoryFile)
            throws Exception
    {
        // check whether we need to unzip the resources first
        if (resourceDirectoryFile.toString().endsWith(".zip")
                && resourceDirectoryFile.isFile())
        {
            resourceDirectoryFile = UnZip.unzip(resourceDirectoryFile);
        }
        // check the existence of the path
        if (resourceDirectoryFile.exists() == false)
            throw new IOException("Directory "
                                          + resourceDirectoryFile.getAbsolutePath()
                                          + " does not exist");
        // check that the lexicon files exists (e.g. its name should be simply
        // 'lexicon')
        File lexiconFile = new File(resourceDirectoryFile,
                                    Parameters.lexiconName);
        if (lexiconFile.exists() == false)
            throw new IOException("Lexicon " + lexiconFile + " does not exist");
        // and that there is a model file
        File modelFile = new File(resourceDirectoryFile, Parameters.modelName);
        if (modelFile.exists() == false)
            throw new IOException("Model " + modelFile + " does not exist");
        Lexicon lexicon = new Lexicon(lexiconFile.toString());
        // ask the Lexicon for the classifier to use
        String classifier = lexicon.getClassifierType();
        TextClassifier instance = (TextClassifier) Class.forName(classifier)
                .newInstance();
        // set the last modification info
        instance.lastmodifiedLexicon = lexiconFile.lastModified();
        // set the pathResourceDirectory
        instance.pathResourceDirectory = resourceDirectoryFile
                .getAbsolutePath();
        // set the model
        instance.lexicon = lexicon;
        instance.loadModel();
        return instance;
    }

    /*****************************************************************************
     * Each instance has its own ways of loading its models
     *
     * @throws IOException
     * @throws Exception
     */
    protected abstract void loadModel() throws Exception;

    /**
     * Returns the probabilities for each label or the raw scores if the model
     * does not support probabilities
     ***/
    public abstract double[] classify(Document document) throws Exception;

    /**
     * Returns the probabilities for each label or the raw scores if the model
     * does not support probabilities
     ***/
    public double[][] classify(List corpus) throws Exception
    {
        Document[] documents = (Document[]) corpus.toArray(new Document[corpus
                .size()]);
        return classify(documents);
    }

    /**
     * Returns the probabilities for each label or the raw scores if the model
     * does not support probabilities
     ***/
    public double[][] classify(Document[] documents) throws Exception
    {
        double[][] predictions = new double[documents.length][lexicon
                .getLabelNum()];
        for (int d = 0; d < documents.length; d++)
        {
            Document doc = documents[d];
            predictions[d] = classify(doc);
        }
        return predictions;
    }

    public Document createDocument(Field[] fields)
    {
        return new MultiFieldDocument(fields, this.lexicon, false);
    }

    // Creates a document using the lexicon
    // this way it is easier to collect the
    // doc frequency and requires less memory

    /**
     * 由一个单词列表创建文档
     * @param tokenstring
     * @return
     */
    public Document createDocument(String[] tokenstring)
    {
        return new SimpleDocument(tokenstring, this.lexicon, false);
    }

    public double platterNormalisation(double x)
    {
        double sigma = 2;
        double tmp = 1 + Math.exp(-sigma * x);
        if (tmp == 0)
            return 0;
        return 1 / tmp;
    }

    public String[] getLabels()
    {
        return this.lexicon.getLabels();
    }

    /***
     * returns the best label for a classification given the array of scores for
     * each label
     **/
    public String getBestLabel(double[] scores)
    {
        int best = 0;
        double bestScore = 0d;
        for (int d = 0; d < scores.length; d++)
        {
            if (scores[d] >= bestScore)
            {
                bestScore = scores[d];
                best = d;
            }
        }
        return this.lexicon.getLabel(best);
    }

    /***
     * Scales the scores between 0 and 1
     **/
    public String[] getBestLabels(double[] scores, float ratioOfBest)
    {
        if (ratioOfBest > 1 | ratioOfBest <= 0)
            throw new RuntimeException(
                    "ratioOfBest shoudl be > 0 and <= 1 but got " + ratioOfBest);
        TreeMap<Double, String> sortedMap = new TreeMap<Double, String>();
        double max = -Double.MAX_VALUE;
        double min = Double.MAX_VALUE;
        for (int d = 0; d < scores.length; d++)
        {
            if (scores[d] > max)
            {
                max = scores[d];
            }
            if (scores[d] < min)
            {
                min = scores[d];
            }
        }

        double scaleFactor = max - min;
        for (int x = 0; x < scores.length; x++)
        {
            double newScore = ((scores[x] - min) / scaleFactor);
            sortedMap.put(newScore, this.lexicon.getLabel(x));
        }

        // find cutoffpoint -> we know that the best label will have a value of 1
        double threshold = ratioOfBest;

        List<String> labelsKept = new ArrayList<String>();
        Iterator<Entry<Double, String>> pairs = sortedMap.entrySet().iterator();
        while (pairs.hasNext())
        {
            Entry<Double, String> pair = pairs.next();
            if (pair.getKey() >= threshold)
                labelsKept.add(pair.getValue());
        }
        return (String[]) labelsKept.toArray(new String[labelsKept.size()]);
    }

    /**
     * Returns true if a new model/lexicon has been generated since the last
     * loading*
     */
    public boolean needsRefreshing()
    {
        // is there a new version available?
        long lastmodified = new File(pathResourceDirectory,
                                     Parameters.lexiconName).lastModified();
        boolean needsRefreshing = false;
        if (lastmodifiedLexicon != lastmodified)
        {
            needsRefreshing = true;
        }
        return needsRefreshing;
    }
}

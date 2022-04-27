import * as React from 'react';

import { StyleSheet, View } from 'react-native';
import { FaceRecognitionView } from 'react-native-face-recognition';

export default function App() {
  return (
    <View style={styles.container}>
      <FaceRecognitionView />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: 'red'
  },
  box: {
    flex: 1
  },
});

import * as React from 'react';

import { StyleSheet, View } from 'react-native';
import { FaceRecognitionView } from 'react-native-face-recognition';

export default function App() {

  const _onGetRect = ({ nativeEvent }) => {
    console.log('------------------------------------');
    console.log('_onGetRect => ', nativeEvent);
    console.log('------------------------------------');
  };

  const _onGetData = ({ nativeEvent }) => {
    console.log('------------------------------------');
    console.log('_onGetData => ', nativeEvent);
    console.log('------------------------------------');
  };

  const _onGetCapture = ({ nativeEvent }) => {
    console.log('------------------------------------');
    console.log('_onGetCapture => ', nativeEvent);
    console.log('------------------------------------');
  };


  return (
    <View style={styles.container}>
      <FaceRecognitionView
        onGetRect={_onGetRect}
        onGetData={_onGetData}
        onGetCapture={_onGetCapture}
      />
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

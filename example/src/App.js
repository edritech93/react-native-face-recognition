import React, { useState, useEffect } from 'react';
import { View, StyleSheet, TouchableOpacity, Text } from 'react-native';
import { FaceRecognitionView } from 'react-native-face-recognition';

let TIME_INTERVAL_COUNT_DOWN;
const VALUE_COUNT_DOWN = 3;


// NOTE: data dummy
const profile = {
  FullName: 'Yudi Edri Alviska'
}

const faceDownload = 'base64 image for sample'
const faceAutoCapture = false

export default function App(props) {
  const [face, setFace] = useState(null);
  const [dataClocking, setDataClocking] = useState(null);
  const [isCapture, setIsCapture] = useState(false);
  const [isCorrectUser, setIsCorrectUser] = useState(false);
  const [countDown, setCountDown] = useState(0);

  useEffect(() => {
    return () => _cleanUp();
  }, []);

  useEffect(() => {
    if (faceAutoCapture) {
      if (isCorrectUser && countDown === 0) {
        setCountDown(VALUE_COUNT_DOWN);
        _setupCountDown();
      } else {
        _clearTimeInterval();
      }
    }
  }, [isCorrectUser]);

  function _setupCountDown() {
    let value = VALUE_COUNT_DOWN;
    TIME_INTERVAL_COUNT_DOWN = setInterval(() => {
      if (value <= 0) {
        _onPressTake();
        _clearTimeInterval();
      } else {
        setCountDown(value--);
      }
    }, 1000);
  }

  function _cleanUp() {
    _clearTimeInterval();
  }

  function _clearTimeInterval() {
    setCountDown(0);
    if (TIME_INTERVAL_COUNT_DOWN) {
      clearInterval(TIME_INTERVAL_COUNT_DOWN);
    }
  }

  const _onGetData = ({nativeEvent}) => {
    setDataClocking(nativeEvent);
    if (nativeEvent && nativeEvent.confidence < 1) {
      setIsCorrectUser(nativeEvent.isHuman);
    } else {
      if (countDown === 0) {
        setIsCorrectUser(false);
      }
    }
  };

  const _onGetCapture = ({nativeEvent}) => {
    if (onPassProps && nativeEvent && nativeEvent.image) {
      onPassProps(nativeEvent.image);
    }
    props.navigation.goBack();
  };

  function _onPressTake() {
    setIsCapture(true);
    setTimeout(() => {
      setIsCapture(false);
    }, 1000);
  }

  let userColor = 'red';
  let userName = 'Unknown';
  if (countDown > 0) {
    userColor = 'green';
    userName = profile.FullName;
  } else {
    if (dataClocking && dataClocking.confidence < 1) {
      userColor = dataClocking.isHuman ? 'green' : 'yellow';
      userName = dataClocking.isHuman ? profile.FullName : 'Check User';
    }
  }

  return (
    <View style={styles.container}>
      <FaceRecognitionView
        style={styles.wrapCamera}
        sample={faceDownload}
        capture={isCapture}
        onGetRect={({nativeEvent}) => setFace(nativeEvent)}
        onGetData={_onGetData}
        onGetCapture={_onGetCapture}
      />
      {face && (
        <View
          style={[
            styles.wrapFace,
            {
              width: face.width,
              height: face.height,
              top: face.y,
              left: face.x,
              borderColor: userColor,
            },
          ]}>
          <Text style={[styles.textUser, { color: userColor }]}>
            {`${userName}`}
          </Text>
        </View>
      )}
      <View style={styles.wrapBottom}>
        {isCorrectUser && !faceAutoCapture && (
          <TouchableOpacity
            style={styles.wrapSnap}
            disabled={isCapture}
            onPress={() => _onPressTake()}>
            <View style={styles.pressTakeView} />
          </TouchableOpacity>
        )}
        {countDown > 0 && <Title style={styles.textCount}>{countDown}</Title>}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingHorizontal: 0,
  },
  wrapCamera: {
    flex: 1,
    backgroundColor: 'black',
  },
  wrapFace: {
    flexDirection: 'row',
    position: 'absolute',
    alignItems: 'center',
    borderWidth: 4,
    borderRadius: 4,
  },
  textUser: {
    flex: 1,
    textAlign: 'center',
    color: 'green',
  },
  wrapBottom: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 16,
  },
  wrapSnap: {
    width: 64,
    height: 64,
    borderColor: 'white',
    borderWidth: 8,
    borderRadius: 32,
    justifyContent: 'center',
    alignItems: 'center',
    alignSelf: 'center',
  },
  pressTakeView: {
    width: 40,
    height: 40,
    backgroundColor: 'white',
    borderRadius: 30,
  },
  textCount: {
    fontSize: 64,
    lineHeight: 70,
    color: 'orange',
    textAlign: 'center',
  },
});

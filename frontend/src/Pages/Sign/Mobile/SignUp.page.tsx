import { useRecoilValue } from 'recoil'
import {
  signUpCheckEmailNumberState,
  signUpCheckPwTextState,
  signUpIdTextState,
  signUpNickNameTextState,
  signUpPwTextState,
  signUpUserNameTextState,
} from '../../../Atoms/Sign/SignUp.atom'
import { ChangeEvent, useEffect, useState } from 'react'
import InputForm from '../../../Elements/Input/InputForm.element'
import { isEmail } from '../../../utils/validator'
import HomeButtonElement from '../../../Elements/Button/HomeButton.element'
import { signUpAxios } from '../sign.axios'

const SignUpMobilePage = () => {
  const signUpId = useRecoilValue(signUpIdTextState)
  const signUpPw = useRecoilValue(signUpPwTextState)
  const signUpCheckPw = useRecoilValue(signUpCheckPwTextState)
  const signUpNickName = useRecoilValue(signUpNickNameTextState)
  const signUpUserName = useRecoilValue(signUpUserNameTextState)
  const [isValidId, setIsValidId] = useState(true)
  const [isValidPw, setisValidPw] = useState(true)
  const [signUpButtonColor, setSignUpButtonColor] = useState('bg-intacitve')
  const canValidSignUp = () =>
    isValidId && isValidPw && signUpId && signUpPw && signUpNickName && signUpUserName

  useEffect(() => {
    localStorage.clear()
    sessionStorage.clear()
  })

  useEffect(() => {
    setIsValidId(signUpId == '' || isEmail(signUpId))
  }, [signUpId])

  useEffect(() => {
    setisValidPw(signUpPw == signUpCheckPw)
  }, [signUpPw, signUpCheckPw])

  useEffect(() => {
    if (canValidSignUp()) {
      setSignUpButtonColor('bg-point')
    } else {
      setSignUpButtonColor('bg-intacitve')
    }
  }, [isValidId, isValidPw, signUpId, signUpPw, signUpUserName, signUpNickName])

  const goSignUp = () => {
    if (canValidSignUp()) {
      signUpAxios(signUpId, signUpPw, signUpNickName, signUpUserName)
    }
  }
  return (
    <div className="w-screen h-screen flex flex-col justify-between items-center px-[10%]">
      <div className="w-full flex flex-col items-center">
        <HomeButtonElement className="p-16 mt-12" />
        <div className="w-full">
          <InputForm
            formTitle="이메일 주소"
            textState={signUpIdTextState}
            className="w-full py-[5%] font-light text-base text-mainText"
            inputTextClassName="text-sm p-2 rounded-sm"
            warningClassName="font-light text-xs !translate-y-[-130%]"
            placeholder="example@smilegate.com"
            isWarning={!isValidId}
            warningText="이메일 형식이 틀렸습니다."
          />
          <InputForm
            textState={signUpPwTextState}
            formTitle="비밀번호"
            className="w-full py-[5%] font-light text-base text-mainText"
            inputTextClassName="text-sm p-2 rounded-sm"
            type="password"
          />
          <InputForm
            textState={signUpCheckPwTextState}
            formTitle="비밀번호 확인"
            className="w-full py-[5%] font-light text-base text-mainText"
            warningClassName="font-light text-xs !translate-y-[-130%]"
            inputTextClassName="text-sm p-2 rounded-sm"
            type="password"
            isWarning={!isValidPw}
            warningText="비밀번호가 맞지 않습니다."
          />
          <InputForm
            textState={signUpNickNameTextState}
            formTitle="닉네임"
            className="w-full py-[5%] font-light text-base text-mainText"
            inputTextClassName="text-sm p-2 rounded-sm"
            placeholder="loopy"
          />
          <InputForm
            textState={signUpUserNameTextState}
            formTitle="이름"
            className="w-full py-[5%] font-light text-base text-mainText"
            inputTextClassName="text-sm p-2 rounded-sm"
            placeholder="임올인"
          />
          {/* <div className="flex py-[5%] items-end">
            <InputForm
              textState={signUpCheckEmailNumberState}
              formTitle="이메일 인증"
              className="w-full font-light text-base text-mainText"
              inputTextClassName="w-full text-base p-2 rounded-sm"
              placeholder="인증번호 입력"
              type="number"
            />
            <button className="h-[50%] w-full ml-4 border-border rounded-sm border-[1px] p-2">
              인증번호 발송
            </button>
          </div> */}
          <button
            className={'text-white rounded-sm w-full p-2 mt-12' + signUpButtonColor}
            onClick={goSignUp}
          >
            가입하기
          </button>
        </div>
      </div>
    </div>
  )
}

export default SignUpMobilePage

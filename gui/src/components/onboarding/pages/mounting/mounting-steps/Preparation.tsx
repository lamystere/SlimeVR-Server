import { useTranslation } from 'react-i18next';
import { ResetType } from 'solarxr-protocol';
import { Button } from '../../../../commons/Button';
import { Typography } from '../../../../commons/Typography';
import { ResetButton } from '../../../../home/ResetButton';

export function PreparationStep({
  nextStep,
  prevStep,
  variant,
}: {
  nextStep: () => void;
  prevStep: () => void;
  variant: 'onboarding' | 'alone';
}) {
  const { t } = useTranslation();

  return (
    <>
      <div className="flex flex-col flex-grow">
        <div className="flex flex-col gap-4 max-w-sm">
          <Typography variant="main-title" bold>
            {t('onboarding.automatic-mounting.preparation.title')}
          </Typography>
          <div>
            <Typography color="secondary">
              {t('onboarding.automatic-mounting.preparation.step.0')}
            </Typography>
            <Typography color="secondary">
              {t('onboarding.automatic-mounting.preparation.step.1')}
            </Typography>
          </div>
        </div>

        <div className="flex flex-grow items-center"></div>
        <div className="flex gap-3">
          <Button
            variant={variant === 'onboarding' ? 'secondary' : 'tiertiary'}
            onClick={prevStep}
          >
            {t('onboarding.automatic-mounting.prev-step')}
          </Button>
          <ResetButton
            variant="small"
            type={ResetType.Full}
            onReseted={nextStep}
          ></ResetButton>
        </div>
      </div>
      <div className="flex flex-col pt-1 items-center fill-background-50 justify-center px-12">
        <img src="/images/reset-pose.png" width={60} />
      </div>
    </>
  );
}